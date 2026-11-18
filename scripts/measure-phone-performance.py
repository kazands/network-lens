#!/usr/bin/env python3
"""Read aggregate metrics from an explicitly selected Pixel 8 while Network Lens stays foreground.

Does not launch, stop, install, clear, scan, take screenshots or export device results.
Start a scan manually if you want to sample its workload. Cold-start timing is not measured.
"""
import argparse
import json
import re
import subprocess
import time
from pathlib import Path

PACKAGE = "io.github.kazands.networklens"
ROOT = Path(__file__).resolve().parent.parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--samples", type=int, default=10)
    parser.add_argument("--interval", type=float, default=3)
    args = parser.parse_args()
    if not 1 <= args.samples <= 30 or not 1 <= args.interval <= 10:
        parser.error("Use 1–30 samples, 1–10 seconds apart")
    output = args.output.resolve()
    if not any(output.is_relative_to(ROOT / folder) for folder in ("artifacts", ".tools", "evidence")):
        parser.error("Use an ignored local evidence directory")
    adb = [str(ROOT / ".tools/sdk/platform-tools/adb"), "-s", args.serial]

    def shell(*command):
        return subprocess.run(adb + ["shell", *command], check=True, text=True, capture_output=True, timeout=15).stdout

    def foreground():
        state = shell("dumpsys", "activity", "activities")
        resumed = re.findall(r"(?:topResumedActivity|mResumedActivity):?\s*=?.*?\bu0\s+([^\s/]+)/", state)
        if not resumed or any(package != PACKAGE for package in resumed):
            raise RuntimeError("Stopped: Network Lens is not the foreground app")
        windows = shell("dumpsys", "window")
        focused = re.findall(r"mCurrentFocus=Window\{[^\r\n}]*\s([^\s/]+)/", windows)
        if not focused or any(package != PACKAGE for package in focused):
            raise RuntimeError("Stopped: another window has foreground focus")

    def read(*command):
        foreground()
        result = shell(*command)
        foreground()
        return result

    # These checks identify only the chosen target; unrelated devices are never queried.
    if shell("getprop", "ro.product.model").strip() != "Pixel 8" or shell("getprop", "ro.kernel.qemu").strip() == "1":
        raise RuntimeError("This physical check requires the explicitly authorized Pixel 8")
    foreground()
    package_path = read("pm", "path", PACKAGE).strip()
    if not re.fullmatch(r"package:/data/app/[^\r\n]+/base\.apk", package_path):
        raise RuntimeError("Unexpected APK location")
    digest = read("sha256sum", package_path[len("package:"):]).split()[0]
    if not re.fullmatch(r"[a-f0-9]{64}", digest):
        raise RuntimeError("APK identity unavailable")
    report = {"environment": "physical Pixel 8", "apkSha256": digest,
              "scope": "Foreground aggregate samples; no cold-start, recognition accuracy or battery-life claim", "samples": []}
    expected_pid = read("pidof", PACKAGE).strip()
    start = time.monotonic()
    try:
        for index in range(args.samples):
            memory = read("dumpsys", "meminfo", PACKAGE)
            frames = read("dumpsys", "gfxinfo", PACKAGE)
            pss = re.search(r"TOTAL PSS:\s*(\d+)", memory)
            total = re.search(r"Total frames rendered:\s*(\d+)", frames)
            janky = re.search(r"Janky frames:\s*(\d+)", frames)
            pid = read("pidof", PACKAGE).strip()
            if not re.fullmatch(r"[0-9]+", pid) or pid != expected_pid:
                raise RuntimeError("App process changed or is unavailable")
            threads = read("ps", "-T", "-p", pid, "-o", "TID")
            report["samples"].append({"elapsedSeconds": round(time.monotonic() - start, 2),
                                      "pssKiB": int(pss[1]) if pss else None,
                                      "renderedFrames": int(total[1]) if total else None,
                                      "jankyFrames": int(janky[1]) if janky else None,
                                      "threads": sum(bool(re.fullmatch(r"\s*[0-9]+\s*", line)) for line in threads.splitlines())})
            if index + 1 < args.samples:
                time.sleep(args.interval)
        if read("sha256sum", package_path[len("package:"):]).split()[0] != digest:
            raise RuntimeError("APK changed during measurement")
        report["status"] = "finished"
    except (RuntimeError, subprocess.SubprocessError) as error:
        report["status"] = "stopped"
        report["reason"] = "Foreground access or target availability changed"
        raise RuntimeError(report["reason"]) from error
    finally:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + "\n")
    print("Recorded " + str(len(report["samples"])) + " foreground performance samples; no network identities exported")


if __name__ == "__main__":
    main()
