#!/usr/bin/env python3
"""Small, private fqnovel audio normalizer and Range server.

The upstream play endpoint returns CENC/AES-CTR encrypted media.  This service
keeps that detail server-side: it obtains a short-lived URL from fqnovel,
downloads one chapter, normalizes it with ffmpeg, and serves an ordinary
progressive file to Media3/ExoPlayer.

Two upstream flavours exist and they do not share a container:

* 真人有声 (``toneId=0``) is AAC and is remuxed into MP4 (``audio/mp4``).
* TTS voices (``toneId`` from ``tts_tones``) are Opus, which MP4 cannot carry.
  Those are remuxed into Ogg (``audio/ogg``) instead, still without re-encoding.

Endpoints:

* ``GET|HEAD /audio/stream/{audioBookId}/{itemId}?toneId=`` - playable audio, Range capable.
* ``GET|POST /audio/warm/{audioBookId}/{itemId}?toneId=``   - non-blocking prepare-ahead.
* ``GET /healthz``                                          - status plus cache counters.
"""

from __future__ import annotations

import base64
import binascii
import json
import logging
import os
import pathlib
import re
import shutil
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


UPSTREAM = os.environ.get("FQNOVEL_UPSTREAM", "http://127.0.0.1:9999").rstrip("/")
CACHE_DIR = pathlib.Path(os.environ.get("AUDIO_CACHE_DIR", "/var/cache/fqnovel-audio"))
HOST = os.environ.get("STREAM_HOST", "127.0.0.1")
PORT = int(os.environ.get("STREAM_PORT", "9998"))
TIMEOUT = float(os.environ.get("UPSTREAM_TIMEOUT", "45"))
MAX_CACHE_BYTES = int(os.environ.get("MAX_CACHE_BYTES", str(20 * 1024**3)))
WARM_WORKERS = max(1, int(os.environ.get("WARM_WORKERS", "2")))
API_TOKEN = os.environ.get("STREAM_API_TOKEN", "").strip()
UPSTREAM_TOKEN = os.environ.get("FQNOVEL_API_TOKEN", "").strip()

ID_RE = re.compile(r"^[0-9]+$")
STREAM_PATH_RE = re.compile(r"/audio/stream/([0-9]+)/([0-9]+)")
WARM_PATH_RE = re.compile(r"/audio/warm/([0-9]+)/([0-9]+)")

# Container choice per upstream codec. ``copy`` never re-encodes; the fallback
# does, and only runs for codecs we have not seen from this upstream.
CONTAINERS = {
    "aac": (".m4a", "audio/mp4", ["-c", "copy", "-movflags", "+faststart"]),
    "mp3": (".mp3", "audio/mpeg", ["-c", "copy"]),
    "opus": (".ogg", "audio/ogg", ["-c", "copy", "-f", "ogg"]),
    "vorbis": (".ogg", "audio/ogg", ["-c", "copy", "-f", "ogg"]),
}
FALLBACK_CONTAINER = (".m4a", "audio/mp4", ["-c:a", "aac", "-b:a", "64k", "-movflags", "+faststart"])
CONTENT_TYPES = {suffix: mime for suffix, mime, _ in list(CONTAINERS.values()) + [FALLBACK_CONTAINER]}

locks: dict[str, threading.Lock] = {}
locks_guard = threading.Lock()
warm_pool = ThreadPoolExecutor(max_workers=WARM_WORKERS, thread_name_prefix="warm")
warming: set[str] = set()
stats_guard = threading.Lock()
stats = {"hits": 0, "misses": 0, "prepared": 0, "failures": 0, "evicted": 0, "warmRequests": 0}


def bump(counter: str, delta: int = 1) -> None:
    with stats_guard:
        stats[counter] = stats.get(counter, 0) + delta


def chapter_lock(key: str) -> threading.Lock:
    with locks_guard:
        return locks.setdefault(key, threading.Lock())


def decode_spade_a(encoded: str) -> bytes:
    try:
        raw = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError("invalid encryption_key") from exc
    if len(raw) < 4:
        raise ValueError("encryption_key wrapper is too short")
    trailer_len = (raw[0] ^ raw[1] ^ raw[2]) - ord("0")
    if trailer_len < 0 or trailer_len >= len(raw) - 1:
        raise ValueError("invalid encryption_key wrapper")
    decoded = bytearray(raw[1 : len(raw) - trailer_len])
    previous_even, previous_odd = 0xFA, 0x55
    for index, value in enumerate(decoded):
        mask = previous_odd if index % 2 else previous_even
        decoded[index] = (0xEB - index.bit_count() + (value ^ mask)) & 0xFF
        if index % 2:
            previous_odd = value
        else:
            previous_even = value
    marker_len = decoded[0]
    marker_len = (marker_len - ord("0")) if ord("0") <= marker_len <= ord("9") else marker_len - ord("a") + 10
    if not 0 < marker_len <= len(decoded) - 2:
        raise ValueError("invalid decoded encryption_key")
    try:
        key = bytes.fromhex(bytes(decoded[1 : len(decoded) - marker_len]).decode("ascii"))
    except (ValueError, UnicodeDecodeError) as exc:
        raise ValueError("invalid decoded AES key") from exc
    if len(key) != 16:
        raise ValueError("decoded AES key is not 16 bytes")
    return key


def json_request(path: str) -> dict:
    headers = {"Accept": "application/json"}
    if UPSTREAM_TOKEN:
        headers["X-Api-Key"] = UPSTREAM_TOKEN
    request = urllib.request.Request(UPSTREAM + path, headers=headers)
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        payload = json.load(response)
    if payload.get("code") != 0 or payload.get("success") is False:
        raise RuntimeError(payload.get("message") or "upstream request failed")
    return payload


def cached_path(audio_book_id: str, item_id: str, tone_id: str) -> pathlib.Path | None:
    stem = f"{audio_book_id}_{tone_id}_{item_id}"
    for suffix in CONTENT_TYPES:
        candidate = CACHE_DIR / f"{stem}{suffix}"
        if candidate.is_file() and candidate.stat().st_size > 0:
            return candidate
    return None


def probe_codec(source: pathlib.Path, aes_key_hex: str) -> str:
    command = [
        "ffprobe", "-v", "error", "-decryption_key", aes_key_hex,
        "-select_streams", "a:0", "-show_entries", "stream=codec_name",
        "-of", "default=nw=1:nk=1", str(source),
    ]
    try:
        result = subprocess.run(command, check=True, capture_output=True, timeout=60)
    except subprocess.SubprocessError as exc:
        logging.warning("codec probe failed, assuming aac: %s", exc)
        return "aac"
    return result.stdout.decode("ascii", "ignore").strip().splitlines()[0].strip() if result.stdout.strip() else "aac"


def download(url: str, target: pathlib.Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "fqnovel-stream/1.0"})
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response, target.open("wb") as output:
        shutil.copyfileobj(response, output, length=1024 * 1024)


def enforce_cache_budget() -> None:
    """Drop the least recently used chapters once the cache exceeds its budget."""
    if MAX_CACHE_BYTES <= 0:
        return
    try:
        entries = []
        total = 0
        for path in CACHE_DIR.iterdir():
            if not path.is_file() or path.suffix not in CONTENT_TYPES:
                continue
            info = path.stat()
            total += info.st_size
            entries.append((max(info.st_atime, info.st_mtime), info.st_size, path))
        if total <= MAX_CACHE_BYTES:
            return
        entries.sort(key=lambda entry: entry[0])
        for _, size, path in entries:
            if total <= MAX_CACHE_BYTES:
                break
            try:
                path.unlink()
            except OSError:
                continue
            total -= size
            bump("evicted")
            logging.info("evicted %s (%d bytes)", path.name, size)
    except OSError as exc:
        logging.warning("cache housekeeping failed: %s", exc)


def prepare_chapter(audio_book_id: str, item_id: str, tone_id: str) -> pathlib.Path:
    key = f"{audio_book_id}/{tone_id}/{item_id}"
    existing = cached_path(audio_book_id, item_id, tone_id)
    if existing:
        bump("hits")
        return existing
    with chapter_lock(key):
        existing = cached_path(audio_book_id, item_id, tone_id)
        if existing:
            bump("hits")
            return existing
        bump("misses")
        play_path = f"/audio/play/{audio_book_id}/{item_id}?toneId={urllib.parse.quote(tone_id)}&download=false"
        data = json_request(play_path).get("data")
        if isinstance(data, list):
            data = data[0] if data else None
        if not isinstance(data, dict):
            raise RuntimeError("upstream returned no audio data")
        source_url = data.get("main_url") or data.get("backup_url")
        wrapped_key = data.get("encryption_key")
        if not source_url or not wrapped_key:
            raise RuntimeError("upstream audio data is incomplete")
        aes_key = decode_spade_a(wrapped_key)
        CACHE_DIR.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="fq-audio-", dir=CACHE_DIR) as tmp:
            encrypted = pathlib.Path(tmp) / "encrypted"
            try:
                download(source_url, encrypted)
            except urllib.error.HTTPError:
                backup = data.get("backup_url")
                if not backup or backup == source_url:
                    raise
                download(backup, encrypted)
            codec = probe_codec(encrypted, aes_key.hex())
            suffix, _, output_args = CONTAINERS.get(codec, FALLBACK_CONTAINER)
            if codec not in CONTAINERS:
                logging.info("unknown codec %s, transcoding to aac", codec)
            normalized = pathlib.Path(tmp) / f"normalized{suffix}"
            command = [
                "ffmpeg", "-hide_banner", "-loglevel", "error",
                "-decryption_key", aes_key.hex(), "-i", str(encrypted), "-map", "0:a:0",
                *output_args, "-y", str(normalized),
            ]
            subprocess.run(command, check=True, timeout=max(180, TIMEOUT * 6))
            if normalized.stat().st_size <= 0:
                raise RuntimeError("ffmpeg produced an empty file")
            target = CACHE_DIR / f"{audio_book_id}_{tone_id}_{item_id}{suffix}"
            os.replace(normalized, target)
        bump("prepared")
        enforce_cache_budget()
    return target


def warm_chapter(audio_book_id: str, item_id: str, tone_id: str) -> str:
    """Kick off preparation without blocking the caller. Returns a coarse status."""
    key = f"{audio_book_id}/{tone_id}/{item_id}"
    if cached_path(audio_book_id, item_id, tone_id):
        return "ready"
    with stats_guard:
        if key in warming:
            return "warming"
        warming.add(key)

    def task() -> None:
        try:
            prepare_chapter(audio_book_id, item_id, tone_id)
        except Exception as exc:  # noqa: BLE001 - background task must not die silently
            bump("failures")
            logging.warning("warm failed for %s: %s", key, exc)
        finally:
            with stats_guard:
                warming.discard(key)

    warm_pool.submit(task)
    bump("warmRequests")
    return "warming"


def cache_usage() -> tuple[int, int]:
    files = 0
    total = 0
    try:
        for path in CACHE_DIR.iterdir():
            if path.is_file() and path.suffix in CONTENT_TYPES:
                files += 1
                total += path.stat().st_size
    except OSError:
        pass
    return files, total


class Handler(BaseHTTPRequestHandler):
    server_version = "fqnovel-stream/0.2"

    def log_message(self, fmt: str, *args: object) -> None:
        logging.info("%s - %s", self.address_string(), fmt % args)

    def send_json(self, status: HTTPStatus, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def send_json_error(self, status: HTTPStatus, message: str) -> None:
        self.send_json(status, {"code": status.value, "message": message})

    def authorized(self, query: dict[str, list[str]]) -> bool:
        if not API_TOKEN:
            return True
        supplied = self.headers.get("X-Api-Key") or query.get("token", [""])[0]
        return supplied == API_TOKEN

    def do_HEAD(self) -> None:
        self.route(send_body=False)

    def do_GET(self) -> None:
        self.route(send_body=True)

    def do_POST(self) -> None:
        self.route(send_body=True)

    def route(self, send_body: bool) -> None:
        split = urllib.parse.urlsplit(self.path)
        query = urllib.parse.parse_qs(split.query)
        if split.path == "/healthz":
            files, total = cache_usage()
            with stats_guard:
                snapshot = dict(stats)
                inflight = len(warming)
            self.send_json(HTTPStatus.OK, {
                "status": "ok",
                "cacheFiles": files,
                "cacheBytes": total,
                "cacheLimitBytes": MAX_CACHE_BYTES,
                "warming": inflight,
                **snapshot,
            })
            return
        if not self.authorized(query):
            self.send_json_error(HTTPStatus.UNAUTHORIZED, "unauthorized")
            return
        warm_match = WARM_PATH_RE.fullmatch(split.path)
        if warm_match:
            self.handle_warm(*warm_match.groups(), query, send_body)
            return
        stream_match = STREAM_PATH_RE.fullmatch(split.path)
        if stream_match:
            self.handle_audio(*stream_match.groups(), query, send_body)
            return
        self.send_json_error(HTTPStatus.NOT_FOUND, "not found")

    def tone_of(self, query: dict[str, list[str]]) -> str | None:
        tone_id = query.get("toneId", ["0"])[0]
        return tone_id if ID_RE.fullmatch(tone_id) else None

    def handle_warm(
        self, audio_book_id: str, item_id: str, query: dict[str, list[str]], send_body: bool
    ) -> None:
        tone_id = self.tone_of(query)
        if tone_id is None:
            self.send_json_error(HTTPStatus.BAD_REQUEST, "invalid toneId")
            return
        try:
            status = warm_chapter(audio_book_id, item_id, tone_id)
        except (BrokenPipeError, ConnectionResetError):
            return
        self.send_json(HTTPStatus.OK, {"code": 0, "status": status})

    def handle_audio(
        self, audio_book_id: str, item_id: str, query: dict[str, list[str]], send_body: bool
    ) -> None:
        tone_id = self.tone_of(query)
        if tone_id is None:
            self.send_json_error(HTTPStatus.BAD_REQUEST, "invalid toneId")
            return
        try:
            path = prepare_chapter(audio_book_id, item_id, tone_id)
            self.serve_file(path, send_body)
        except (BrokenPipeError, ConnectionResetError):
            # Media players routinely cancel probe/range requests after learning
            # enough metadata. The response may already have started, so do not
            # attempt to write a second JSON response to the closed socket.
            return
        except (OSError, RuntimeError, ValueError, urllib.error.URLError, subprocess.SubprocessError) as exc:
            bump("failures")
            logging.warning("audio preparation failed for %s/%s: %s", audio_book_id, item_id, exc)
            message = "audio upstream unavailable" if "ILLEGAL_ACCESS" in str(exc) else "audio preparation failed"
            try:
                self.send_json_error(HTTPStatus.BAD_GATEWAY, message)
            except (BrokenPipeError, ConnectionResetError):
                pass

    def serve_file(self, path: pathlib.Path, send_body: bool) -> None:
        size = path.stat().st_size
        os.utime(path, None)  # keep the LRU order honest
        start, end = 0, size - 1
        range_header = self.headers.get("Range")
        if range_header:
            match = re.fullmatch(r"bytes=(\d*)-(\d*)", range_header.strip())
            if not match:
                self.send_response(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                self.send_header("Content-Range", f"bytes */{size}")
                self.end_headers()
                return
            left, right = match.groups()
            if left:
                start = int(left)
                end = int(right) if right else size - 1
            elif right:
                length = int(right)
                start = max(0, size - length)
            if start >= size or end < start:
                self.send_response(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                self.send_header("Content-Range", f"bytes */{size}")
                self.end_headers()
                return
            end = min(end, size - 1)
        length = end - start + 1
        self.send_response(HTTPStatus.PARTIAL_CONTENT if range_header else HTTPStatus.OK)
        self.send_header("Content-Type", CONTENT_TYPES.get(path.suffix, "application/octet-stream"))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        if range_header:
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Cache-Control", "public, max-age=86400")
        self.end_headers()
        if send_body:
            with path.open("rb") as source:
                source.seek(start)
                remaining = length
                while remaining:
                    chunk = source.read(min(1024 * 1024, remaining))
                    if not chunk:
                        break
                    try:
                        self.wfile.write(chunk)
                    except (BrokenPipeError, ConnectionResetError):
                        return
                    remaining -= len(chunk)


def housekeeping_loop() -> None:
    while True:
        time.sleep(3600)
        enforce_cache_budget()


def main() -> None:
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(message)s")
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    enforce_cache_budget()
    threading.Thread(target=housekeeping_loop, name="housekeeping", daemon=True).start()
    logging.info(
        "serving %s on %s:%s (cache limit %.1f GiB, auth %s)",
        UPSTREAM, HOST, PORT, MAX_CACHE_BYTES / 1024**3, "on" if API_TOKEN else "off",
    )
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
