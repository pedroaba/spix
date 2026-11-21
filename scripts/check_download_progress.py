#!/usr/bin/env python3
"""Exercise the pinned yt-dlp progress protocol with a local throttled, synthetic MP4."""
import http.server
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time

if sys.version_info < (3, 11):
    raise SystemExit('This integration check requires Python 3.11 or newer, matching yt-dlp')

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/pedroaba/spix/download/DownloadProgress.kt').read_text()
template = re.search(r'DOWNLOAD_TEMPLATE = """(.*?)"""', source).group(1)
processing = re.search(r'PROCESSING_TEMPLATE = "(.*?)"', source).group(1)
ffmpeg = shutil.which('ffmpeg')
if not ffmpeg:
    raise SystemExit('ffmpeg is required for this local integration check')

with tempfile.TemporaryDirectory(prefix='spix-progress-') as temporary:
    directory = Path(temporary)
    fixture = directory / 'fixture.mp4'
    subprocess.run([ffmpeg, '-hide_banner', '-loglevel', 'error', '-f', 'lavfi', '-i', 'testsrc2=size=640x360:rate=24',
                    '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=44100', '-t', '2', '-c:v', 'libx264',
                    '-preset', 'ultrafast', '-b:v', '2M', '-c:a', 'aac', '-movflags', '+faststart', str(fixture)], check=True)
    payload = fixture.read_bytes()

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_HEAD(self):
            self.send_response(200)
            self.send_header('Content-Type', 'video/mp4')
            self.send_header('Content-Length', str(len(payload)))
            self.end_headers()

        def do_GET(self):
            self.do_HEAD()
            try:
                for start in range(0, len(payload), 8192):
                    self.wfile.write(payload[start:start + 8192])
                    self.wfile.flush()
                    time.sleep(.035)
            except (BrokenPipeError, ConnectionResetError):
                pass

        def log_message(self, *_):
            pass

    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        result = subprocess.run([sys.executable, str(root / 'app/src/main/res/raw/ytdlp'), '--ignore-config',
                                 '--newline', '--progress', '--progress-delta', '0.25', '--no-colors', '--output-na-placeholder', 'null',
                                 '--progress-template', template, '--progress-template', processing,
                                 '--remux-video', 'mp4', '--ffmpeg-location', ffmpeg,
                                 '-o', str(directory / 'download.%(ext)s'),
                                 f'http://127.0.0.1:{server.server_port}/fixture.mp4'],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=45)
        if result.returncode:
            raise SystemExit(result.stdout[-2000:])
        events = [json.loads(line.removeprefix('SPIX_TRANSFER:')) for line in result.stdout.splitlines() if line.startswith('SPIX_TRANSFER:')]
        assert len(events) >= 3, 'No intermediate native progress events'
        assert any(0 < event['downloaded'] < len(payload) for event in events), 'No partial transfer was reported'
        assert events[-1]['status'] == 'finished'
        assert events[-1]['downloaded'] == len(payload)
        assert (directory / 'download.mp4').read_bytes() == payload
        print(json.dumps({'progress_events': len(events), 'bytes': len(payload),
                          'intermediate_bytes': [event['downloaded'] for event in events[:-1]],
                          'postprocess_events': result.stdout.count('SPIX_PROCESS:')}, indent=2))
    finally:
        server.shutdown()
        server.server_close()
