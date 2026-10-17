#!/usr/bin/env python3
"""Smoke-test only a disposable emulator. --allow-reset explicitly permits clearing this app's data."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--adb", default="adb")
parser.add_argument("--serial", default="emulator-5554")
parser.add_argument("--allow-reset", action="store_true", required=True)
args = parser.parse_args()
if not args.serial.startswith("emulator-"):
    raise SystemExit("Only a disposable emulator is allowed")
root = Path(__file__).resolve().parents[1]
output = root / "artifacts"
output.mkdir(exist_ok=True)
steps = []


def adb(*command, binary=False):
    return subprocess.check_output([args.adb, "-s", args.serial, *command], timeout=30, text=not binary)


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/attend-smoke.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/attend-smoke.xml"))


def find(text=None, cls=None, timeout=10):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        ui = tree()
        for node in ui.iter("node"):
            if (text is None or node.get("text") == text) and (cls is None or node.get("class") == cls):
                return node
        time.sleep(0.2)
    raise AssertionError(f"UI element not found: {text or cls}")


def tap(text=None, cls=None):
    node = find(text, cls)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def screenshot(name):
    (output / name).write_bytes(adb("exec-out", "screencap", "-p", binary=True))


def scroll_to(text):
    for _ in range(6):
        ui = tree()
        if any(n.get("text") == text for n in ui.iter("node")):
            tap(text)
            return
        adb("shell", "input", "swipe", "550", "1800", "550", "650", "350")
    raise AssertionError(f"Cannot scroll to {text}")


adb("shell", "pm", "clear", "in.attend.app")
adb("shell", "am", "start", "-n", "in.attend.app/.MainActivity")
find("Connect college ERP")
screenshot("welcome.png")
steps.append("Clean launch shows real-data onboarding")
tap("Explore with sample data")
find("87.03%")
find("DEMO — sample data")
screenshot("overview.png")
steps.append("Sample mode is labelled and shows 161/185 = 87.03%")
tap("Calendar")
scroll_to("Add absence or calendar change")
find("Calendar change")
tap("Save change")
steps.append("Planned absence saves from Calendar")
screenshot("calendar.png")
tap("Forecast")
tap("Use planned absences")
find("PROJECTED ATTENDANCE")
screenshot("forecast.png")
steps.append("Forecast renders planned-absence scenario")
tap("Settings")
tap("Edit weekly timetable")
mon = find("MON")
_, my1, _, my2 = map(int, re.findall(r"\d+", mon.get("bounds")))
adb("shell", "input", "swipe", "850", str((my1 + my2) // 2), "250", str((my1 + my2) // 2), "350")
tap("FRI")
ui = tree()
ifs = [n for n in ui.iter("node") if "International Financial System" in n.get("text", "")]
assert len(ifs) == 2, "Both Friday IFS sessions must remain separate"
tap("Cancel")
steps.append("Timetable editor retains both Friday IFS periods")
scroll_to("Leave sample preview")
tap("Settings")
tap(cls="android.widget.EditText")
adb("shell", "input", "keyevent", "123")
adb("shell", "input", "keyevent", "67")
adb("shell", "input", "keyevent", "67")
adb("shell", "input", "text", "80")
tap("Save")
adb("shell", "input", "keyevent", "4")
adb("shell", "am", "force-stop", "in.attend.app")
adb("shell", "am", "start", "-n", "in.attend.app/.MainActivity")
tap("Settings")
find("80")
steps.append("Target setting persists through process restart without ERP")
tap("Overview")
tap("Connect college ERP")
find("Connect ERP")
deadline = time.monotonic() + 25
while time.monotonic() < deadline:
    ui = tree()
    if any("Sign in on the official ERP page" in n.get("text", "") for n in ui.iter("node")):
        steps.append("Live ERP sign-in page loads inside restricted WebView; no credentials entered")
        break
    time.sleep(.3)
else:
    raise AssertionError("Official ERP login did not load")
screenshot("erp-login.png")
adb("shell", "input", "keyevent", "4")
tap("Settings")
scroll_to("Disconnect ERP / clear personal data")
tap("Sign out")
find("Connect college ERP")
steps.append("Disconnect is available before first import and returns to clean onboarding")
(output / "smoke-results.json").write_text(json.dumps({"passed": len(steps), "steps": steps, "real_student_import_verified": False}, indent=2) + "\n")
print(json.dumps({"passed": len(steps), "steps": steps}, indent=2))
