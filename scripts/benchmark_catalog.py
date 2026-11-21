#!/usr/bin/env python3
"""Runs the isolated, release-like catalog benchmark and retains raw evidence."""
import argparse
import json
import os
from pathlib import Path
import shutil
import time
import re
import statistics
import subprocess

TARGET = "com.pedroaba.spix.benchmark"
DRIVER = "com.pedroaba.spix.macrobenchmark"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", required=True)
    parser.add_argument("--method", choices=["macro", "hwui"], default="macro")
    parser.add_argument("--sizes", type=int, nargs="+", default=[19, 100, 400, 1000])
    parser.add_argument("--screens", nargs="+", choices=["home", "library", "downloads"], default=["home"])
    parser.add_argument("--output", type=Path, default=Path("/tmp/spix-performance"))
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", args.label):
        parser.error("Label must contain only letters, numbers, dots, underscores or hyphens")
    if args.method == "hwui" and args.screens != ["home"]:
        parser.error("The HWUI diagnostic currently covers only the home screen")
    adb = shutil.which("adb") or str(Path.home() / "Library/Android/sdk/platform-tools/adb")
    root = args.output / args.label
    root.mkdir(parents=True, exist_ok=True)
    def run(*cmd):
        return subprocess.run([adb, *cmd], check=True, capture_output=True, text=True).stdout
    for size in args.sizes:
        if not 0 <= size <= 5000:
            parser.error("Fixture size must be between 0 and 5000")
        for screen in args.screens:
            key = f"{screen}-{size}"
            print(f"Running {args.label}/{key}", flush=True)
            dest = root / key
            dest.mkdir(exist_ok=True)
            remote = f"/sdcard/Android/media/{DRIVER}/{args.label}-{key}"
            result = run("shell", "am", "instrument", "-w", "-r", "-e", "class",
                ("com.pedroaba.spix.benchmark.CatalogBenchmark" if args.method == "macro" else "com.pedroaba.spix.benchmark.CatalogFrameStatsTest"), "-e", "catalogSize", str(size),
                "-e", "catalogScreen", screen, "-e", "additionalTestOutputDir", remote,
                f"{DRIVER}/androidx.test.runner.AndroidJUnitRunner")
            (dest / "instrumentation.txt").write_text(result)
            if "FAILURES" in result or "INSTRUMENTATION_FAILED" in result or "shortMsg=" in result:
                raise RuntimeError(result)
            found = re.search(r"catalogPssKiB=([0-9,]+)", result)
            samples = [int(value) for value in found.group(1).split(",")] if found else []
            (dest / "memory-summary.json").write_text(json.dumps({"pssKiB": samples, "medianPssKiB": statistics.median(samples) if samples else None}, indent=2))
            jank = re.search(r"catalogJankPercent=([0-9.,]+)", result)
            if jank:
                (dest / "hwui-summary.json").write_text(json.dumps({"jankyFramesPercent": [float(n) for n in jank.group(1).split(",")]}, indent=2))
            if args.method == "macro":
                run("pull", remote, str(dest / "traces"))
            print(result[-1400:], flush=True)
    (root / "device.txt").write_text(run("shell", "getprop", "ro.product.model"))

if __name__ == "__main__":
    main()
