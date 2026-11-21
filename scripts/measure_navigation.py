#!/usr/bin/env python3
"""Collect repeatable on-device HWUI navigation diagnostics without changing app data."""
import argparse
import atexit
import signal
import sys
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--rounds', type=int, default=5)
parser.add_argument('--adb', default=str(Path.home() / 'Library/Android/sdk/platform-tools/adb'))
args = parser.parse_args()
package = 'com.pedroaba.spix'

def adb(*command):
    return subprocess.check_output([args.adb, '-s', args.serial, *command], text=True)

def tree():
    for _ in range(4):
        adb('shell', 'rm', '-f', '/sdcard/spix-navigation.xml')
        adb('shell', 'uiautomator', 'dump', '/sdcard/spix-navigation.xml')
        try:
            root = ET.fromstring(adb('shell', 'cat', '/sdcard/spix-navigation.xml'))
            if any(node.get('package') == package and (node.get('text') or node.get('content-desc')) for node in root.iter('node')):
                return root
        except (ET.ParseError, subprocess.CalledProcessError):
            pass
        time.sleep(.5)
    raise RuntimeError('Spix UI is unavailable; unlock the phone and open Spix')

def center(root, label):
    matches = [node for node in root.iter('node') if node.get('text') == label or node.get('content-desc') == label]
    if not matches:
        raise RuntimeError(f'Missing Spix control: {label}')
    left, top, right, bottom = map(int, re.findall(r'\d+', matches[-1].get('bounds')))
    return str((left + right) // 2), str((top + bottom) // 2)

# Keep only the measurement awake; preserve the user's original Android setting.
original_stay_awake = adb('shell', 'settings', 'get', 'global', 'stay_on_while_plugged_in').strip()

def restore_display_policy():
    if original_stay_awake == 'null':
        adb('shell', 'settings', 'delete', 'global', 'stay_on_while_plugged_in')
    else:
        adb('shell', 'settings', 'put', 'global', 'stay_on_while_plugged_in', original_stay_awake)

atexit.register(restore_display_policy)
signal.signal(signal.SIGTERM, lambda *_: sys.exit(143))
adb('shell', 'svc', 'power', 'stayon', 'usb')
adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
adb('shell', 'am', 'start', '-W', '-n', package + '/.MainActivity')
root = tree()
home = center(root, 'Início')
adb('shell', 'input', 'tap', *home)
time.sleep(.7)
root = tree()
positions = {label: center(root, label) for label in ('Início', 'Downloads', 'Biblioteca', 'Abrir configurações', 'Buscar músicas')}

def cycle():
    for label in ('Downloads', 'Biblioteca', 'Início', 'Abrir configurações'):
        adb('shell', 'input', 'tap', *positions[label])
        time.sleep(.65)
    adb('shell', 'input', 'keyevent', 'BACK')
    time.sleep(.65)
    adb('shell', 'input', 'tap', *positions['Buscar músicas'])
    time.sleep(.65)
    adb('shell', 'input', 'keyevent', 'BACK')
    time.sleep(.65)

for _ in range(2):
    cycle()
adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
for _ in range(args.rounds):
    cycle()
raw = adb('shell', 'dumpsys', 'gfxinfo', package, 'framestats')
args.output.parent.mkdir(parents=True, exist_ok=True)
sanitized = "\n".join(line for line in raw.splitlines() if not line.strip().startswith(('ProcessConfig=', 'Application ResourcesConfig=')))
args.output.with_suffix('.txt').write_text(sanitized + "\n")
summary = {}
for key, pattern in {
    'frames': r'Total frames rendered: (\d+)',
    'janky_frames': r'Janky frames: (\d+)',
    'janky_percent': r'Janky frames: \d+ \(([\d.]+)%\)',
    'p50_ms': r'50th percentile: (\d+)ms',
    'p90_ms': r'90th percentile: (\d+)ms',
    'p95_ms': r'95th percentile: (\d+)ms',
    'p99_ms': r'99th percentile: (\d+)ms',
}.items():
    match = re.search(pattern, raw)
    if match:
        summary[key] = float(match[1]) if '.' in match[1] else int(match[1])
report = {
    'model': adb('shell', 'getprop', 'ro.product.model').strip(),
    'sdk': adb('shell', 'getprop', 'ro.build.version.sdk').strip(),
    'rounds': args.rounds,
    'transitions': args.rounds * 7,
    'settle_seconds': .65,
    'summary': summary,
    'method': 'HWUI gfxinfo; two warmup rounds; fixed taps; no Spix data or preference changes; USB stay-awake restored on exit',
}
args.output.write_text(json.dumps(report, indent=2) + '\n')
adb('shell', 'rm', '-f', '/sdcard/spix-navigation.xml')
print(json.dumps(report, indent=2))
