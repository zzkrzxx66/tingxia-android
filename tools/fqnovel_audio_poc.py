#!/usr/bin/env python3
"""Decode fqnovel's spade_a wrapper and normalize one encrypted audio file.

This is a diagnostic PoC for a user-authorized sample. It deliberately keeps
the upstream API call and CDN URL handling outside this script.
"""

from __future__ import annotations

import argparse
import base64
import binascii
import pathlib
import subprocess
import sys


def decode_spade_a(encoded: str) -> tuple[int, bytes]:
    """Return (method, AES key) from the play-info encryption_key field."""
    try:
        raw = base64.b64decode(encoded, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError("encryption_key is not valid base64") from exc
    if len(raw) < 4:
        raise ValueError("encryption_key wrapper is too short")

    trailer_len = (raw[0] ^ raw[1] ^ raw[2]) - ord("0")
    if trailer_len < 0 or trailer_len >= len(raw) - 1:
        raise ValueError("invalid wrapper trailer length")

    decoded = bytearray(raw[1 : len(raw) - trailer_len])
    previous_even = 0xFA
    previous_odd = 0x55
    for index, value in enumerate(decoded):
        mask = previous_odd if index % 2 else previous_even
        decoded[index] = (0xEB - index.bit_count() + (value ^ mask)) & 0xFF
        if index % 2:
            previous_odd = value
        else:
            previous_even = value

    if not decoded:
        raise ValueError("decoded wrapper is empty")
    marker_len = decoded[0]
    marker_len = marker_len - ord("0") if ord("0") <= marker_len <= ord("9") else marker_len - ord("a") + 10
    if not 0 < marker_len <= len(decoded) - 2:
        raise ValueError("invalid decoded marker length")

    key_hex = bytes(decoded[1 : len(decoded) - marker_len]).decode("ascii")
    marker = bytes(decoded[-marker_len:]).decode("ascii")
    try:
        key = bytes.fromhex(key_hex)
        method_id = int(marker, 16)
    except ValueError as exc:
        raise ValueError("decoded wrapper contains invalid hexadecimal data") from exc
    if len(key) != 16:
        raise ValueError(f"expected a 16-byte AES key, got {len(key)} bytes")
    return method_id, key


def normalize_audio(input_path: pathlib.Path, output_path: pathlib.Path, key: bytes) -> None:
    """Decrypt CENC AES-CTR audio and remove encryption metadata via FFmpeg."""
    output_path.parent.mkdir(parents=True, exist_ok=True)
    command = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "error",
        "-decryption_key",
        key.hex(),
        "-i",
        str(input_path),
        "-map",
        "0:a:0",
        "-c",
        "copy",
        "-movflags",
        "+faststart",
        "-y",
        str(output_path),
    ]
    subprocess.run(command, check=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--encryption-key", required=True, help="play-info encryption_key")
    parser.add_argument("input", type=pathlib.Path, help="downloaded encrypted M4A/MP4")
    parser.add_argument("output", type=pathlib.Path, help="decrypted ordinary M4A/MP4")
    args = parser.parse_args()

    try:
        method, key = decode_spade_a(args.encryption_key)
        print(f"method={method} key_bytes={len(key)}")
        normalize_audio(args.input, args.output, key)
    except (OSError, subprocess.CalledProcessError, ValueError) as exc:
        print(f"PoC failed: {exc}", file=sys.stderr)
        return 1
    print(f"wrote={args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
