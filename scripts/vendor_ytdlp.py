#!/usr/bin/env python3
"""Refresh the pinned official extractor resource; verify before replacing the file."""
import hashlib
from pathlib import Path
import subprocess
import tempfile

VERSION = "2026.10.26"
SHA256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"
URL = f"https://github.com/yt-dlp/yt-dlp/releases/download/{VERSION}/yt-dlp"
output = Path(__file__).resolve().parents[1] / "app/src/main/res/raw/ytdlp"
with tempfile.TemporaryDirectory() as directory:
    downloaded = Path(directory) / "yt-dlp"
    subprocess.run(["curl", "--fail", "--location", "--silent", "--show-error", URL, "--output", str(downloaded)], check=True)
    payload = downloaded.read_bytes()
    if hashlib.sha256(payload).hexdigest() != SHA256:
        raise SystemExit("Unexpected yt-dlp SHA-256; destination left unchanged")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(payload)
print(f"yt-dlp {VERSION}: SHA-256 verified")
