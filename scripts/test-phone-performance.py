#!/usr/bin/env python3
"""Offline tests of physical-target and foreground fences. No ADB process is started."""
import contextlib
import importlib.util
import io
import json
import subprocess
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("phone_metrics", Path(__file__).with_name("measure-phone-performance.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PhoneMetricsTests(unittest.TestCase):
    def run_case(self, folder, model="Pixel 8", foreground_loss=False, window_loss=False):
        calls = []
        def fake(command, **kwargs):
            args = command[command.index("shell") + 1:]
            calls.append(args)
            if args == ["getprop", "ro.product.model"]: result = model
            elif args == ["getprop", "ro.kernel.qemu"]: result = ""
            elif args == ["dumpsys", "activity", "activities"]:
                package = "synthetic.other" if foreground_loss else module.PACKAGE
                result = "topResumedActivity=ActivityRecord{123 u0 " + package + "/.MainActivity t1}"
            elif args == ["dumpsys", "window"]:
                package = "synthetic.overlay" if window_loss else module.PACKAGE
                result = "mCurrentFocus=Window{123 u0 " + package + "/.MainActivity}"
            elif args[:2] == ["pm", "path"]: result = "package:/data/app/synthetic/base.apk"
            elif args[0] == "sha256sum": result = "a" * 64 + "  /data/app/synthetic/base.apk"
            elif args[0] == "pidof": result = "123"
            elif args[:2] == ["dumpsys", "meminfo"]: result = "TOTAL PSS: 12345"
            elif args[:2] == ["dumpsys", "gfxinfo"]: result = "Total frames rendered: 42\nJanky frames: 2 (4%)"
            elif args[0] == "ps": result = "TID\n123\n124\n"
            else: raise AssertionError("Unexpected phone command")
            return subprocess.CompletedProcess(command, 0, result, "")
        output = Path(folder) / "metrics.json"
        argv = ["measure-phone-performance.py", "--serial", "synthetic-authorized-target", "--output", str(output), "--samples", "1"]
        with patch("sys.argv", argv), patch.object(module.subprocess, "run", side_effect=fake), contextlib.redirect_stdout(io.StringIO()):
            if foreground_loss or window_loss or model != "Pixel 8":
                with self.assertRaises(RuntimeError): module.main()
                self.assertFalse(output.exists())
            else:
                module.main()
                report = json.loads(output.read_text())
                self.assertEqual(report["samples"][0]["pssKiB"], 12345)
                self.assertEqual(report["samples"][0]["threads"], 2)
                self.assertEqual(report["status"], "finished")
        return calls

    def test_foreground_loss_stops_before_inspection(self):
        with tempfile.TemporaryDirectory(dir=module.ROOT / ".tools") as folder:
            calls = self.run_case(folder, foreground_loss=True)
        self.assertFalse(any(c[0] == "pm" or c[:2] == ["dumpsys", "meminfo"] for c in calls))

    def test_unrelated_device_is_rejected(self):
        with tempfile.TemporaryDirectory(dir=module.ROOT / ".tools") as folder:
            calls = self.run_case(folder, model="Synthetic other model")
        self.assertEqual(calls, [["getprop", "ro.product.model"]])

    def test_overlay_focus_stops_before_inspection(self):
        with tempfile.TemporaryDirectory(dir=module.ROOT / ".tools") as folder:
            calls = self.run_case(folder, window_loss=True)
        self.assertFalse(any(c[0] == "pm" for c in calls))

    def test_reads_only_selected_target_metrics(self):
        with tempfile.TemporaryDirectory(dir=module.ROOT / ".tools") as folder:
            calls = self.run_case(folder)
        self.assertTrue(all(c[0] in {"getprop", "dumpsys", "pm", "sha256sum", "pidof", "ps"} for c in calls))


if __name__ == "__main__":
    unittest.main()
