#!/usr/bin/env python3
"""Small, private fqnovel audio normalizer and Range server.

The upstream play endpoint returns CENC/AES-CTR encrypted M4A.  This service
keeps that detail server-side: it obtains a short-lived URL from fqnovel,
downloads one chapter, normalizes it with ffmpeg, and serves the resulting
ordinary M4A to Media3/ExoPlayer.
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
import urllib.error
import urllib.parse
import urllib.request
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


UPSTREAM = os.environ.get("FQNOVEL_UPSTREAM", "http://127.0.0.1:9999").rstrip("/")
CACHE_DIR = pathlib.Path(os.environ.get("AUDIO_CACHE_DIR", "/var/cache/fqnovel-audio"))
HOST = os.environ.get("STREAM_HOST", "127.0.0.1")
PORT = int(os.environ.get("STREAM_PORT", "9998"))
TIMEOUT = float(os.environ.get("UPSTREAM_TIMEOUT", "45"))
ID_RE = re.compile(r"^[0-9]+$")
locks: dict[str, threading.Lock] = {}
locks_guard = threading.Lock()


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
    request = urllib.request.Request(UPSTREAM + path, headers={"Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        payload = json.load(response)
    if payload.get("code") != 0 or payload.get("success") is False:
        raise RuntimeError(payload.get("message") or "upstream request failed")
    return payload


def prepare_chapter(audio_book_id: str, item_id: str, tone_id: str) -> pathlib.Path:
    key = f"{audio_book_id}/{tone_id}/{item_id}"
    target = CACHE_DIR / f"{audio_book_id}_{tone_id}_{item_id}.m4a"
    if target.is_file() and target.stat().st_size > 0:
        return target
    with chapter_lock(key):
        if target.is_file() and target.stat().st_size > 0:
            return target
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
            encrypted = pathlib.Path(tmp) / "encrypted.m4a"
            normalized = pathlib.Path(tmp) / "normalized.m4a"
            request = urllib.request.Request(source_url, headers={"User-Agent": "fqnovel-stream/1.0"})
            try:
                with urllib.request.urlopen(request, timeout=TIMEOUT) as response, encrypted.open("wb") as output:
                    shutil.copyfileobj(response, output, length=1024 * 1024)
            except urllib.error.HTTPError as first_error:
                backup = data.get("backup_url")
                if not backup:
                    raise
                request = urllib.request.Request(backup, headers={"User-Agent": "fqnovel-stream/1.0"})
                with urllib.request.urlopen(request, timeout=TIMEOUT) as response, encrypted.open("wb") as output:
                    shutil.copyfileobj(response, output, length=1024 * 1024)
            command = [
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-decryption_key", aes_key.hex(),
                "-i", str(encrypted), "-map", "0:a:0", "-c", "copy", "-movflags", "+faststart", "-y", str(normalized),
            ]
            subprocess.run(command, check=True, timeout=max(120, TIMEOUT * 4))
            if normalized.stat().st_size <= 0:
                raise RuntimeError("ffmpeg produced an empty file")
            os.replace(normalized, target)
    return target


class Handler(BaseHTTPRequestHandler):
    server_version = "fqnovel-stream/0.1"

    def log_message(self, fmt: str, *args: object) -> None:
        logging.info("%s - %s", self.address_string(), fmt % args)

    def send_json_error(self, status: HTTPStatus, message: str) -> None:
        body = json.dumps({"code": status.value, "message": message}, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def do_HEAD(self) -> None:
        self.handle_audio(send_body=False)

    def do_GET(self) -> None:
        if self.path == "/healthz":
            body = b'{"status":"ok"}'
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        self.handle_audio(send_body=True)

    def handle_audio(self, send_body: bool) -> None:
        match = re.fullmatch(r"/audio/stream/([0-9]+)/([0-9]+)", urllib.parse.urlsplit(self.path).path)
        if not match:
            self.send_json_error(HTTPStatus.NOT_FOUND, "not found")
            return
        audio_book_id, item_id = match.groups()
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query)
        tone_id = query.get("toneId", ["0"])[0]
        if not ID_RE.fullmatch(tone_id):
            self.send_json_error(HTTPStatus.BAD_REQUEST, "invalid toneId")
            return
        try:
            path = prepare_chapter(audio_book_id, item_id, tone_id)
            self.serve_file(path, send_body)
        except (OSError, RuntimeError, ValueError, urllib.error.URLError, subprocess.SubprocessError) as exc:
            logging.warning("audio preparation failed for %s/%s: %s", audio_book_id, item_id, exc)
            self.send_json_error(HTTPStatus.BAD_GATEWAY, "audio preparation failed")

    def serve_file(self, path: pathlib.Path, send_body: bool) -> None:
        size = path.stat().st_size
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
        self.send_header("Content-Type", "audio/mp4")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        self.send_header("Content-Range", f"bytes {start}-{end}/{size}") if range_header else None
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
                    self.wfile.write(chunk)
                    remaining -= len(chunk)


def main() -> None:
    logging.basicConfig(level=os.environ.get("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(message)s")
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    logging.info("serving %s on %s:%s", UPSTREAM, HOST, PORT)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
