#!/usr/bin/env python3
"""Exercise v0.2 on a disposable emulator dated October 2026. Never use student credentials."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--adb', default='adb')
p.add_argument('--serial', default='emulator-5580')
p.add_argument('--output-dir', type=Path, help='Directory for screenshots and smoke results')
p.add_argument('--allow-reset', action='store_true', required=True)
args = p.parse_args()
if not args.serial.startswith('emulator-'):
    raise SystemExit('Disposable emulator required')
out = args.output_dir or Path(__file__).resolve().parents[1] / 'artifacts' / 'v0.2.0'
out.mkdir(parents=True, exist_ok=True)
steps = []

def record_step(message):
    steps.append(message)
    print('PASS: ' + message, flush=True)

def adb(*words, binary=False):
    return subprocess.check_output([args.adb, '-s', args.serial, *words], text=not binary, timeout=40)

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/attend-v02-smoke.xml')
    return ET.fromstring(adb('exec-out', 'cat', '/sdcard/attend-v02-smoke.xml'))

def find(text=None, prefix=None, suffix=None, desc=None, index=0):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        matches = [n for n in tree().iter('node') if
            (text is None or n.get('text') == text) and
            (prefix is None or n.get('text', '').startswith(prefix)) and
            (suffix is None or n.get('text', '').endswith(suffix)) and
            (desc is None or n.get('content-desc') == desc)]
        if len(matches) > index:
            return matches[index]
    raise AssertionError(f'Missing {text or prefix or desc}')

def tap_node(n):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

def tap(text=None, **kw):
    tap_node(find(text, **kw))

def shot(name):
    (out / name).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))

def scroll_to(text):
    for _ in range(8):
        for n in tree().iter('node'):
            if n.get('text') == text:
                tap_node(n)
                return
        adb('shell', 'input', 'swipe', '540', '1850', '540', '700', '350')
    raise AssertionError(f'Cannot scroll to {text}')

def choose(day, prefix='Date ·'):
    tap(prefix=prefix)
    tap(suffix=day)
    tap('Choose date')

adb('shell', 'pm', 'clear', 'in.attend.app')
adb('shell', 'am', 'start', '-n', 'in.attend.app/.MainActivity')
find('Log attendance')
find('Navratri break · college confirmed')
shot('welcome.png')
record_step('Clean launch prioritizes student tracking and shows confirmed Navratri break')
tap('Add starting counts')
for index, value in [(0, '75'), (1, '100')]:
    fields = [n for n in tree().iter('node') if n.get('class') == 'android.widget.EditText']
    tap_node(fields[index])
    adb('shell', 'input', 'text', value)
    adb('shell', 'input', 'keyevent', '4')
choose('Thursday, October 15, 2026', 'Counts through ·')
tap('Save starting counts')
find('75 / 100 classes')
record_step('Manual starting counts save without any ERP login')
tap('Calendar')
choose('Friday, October 16, 2026')
tap('Present')
find(prefix='Your counter: 76 / 101')
tap('Absent', index=1)
find(prefix='Your counter: 76 / 102')
find('1 present · 1 absent · 3 unmarked')
shot('calendar.png')
record_step('Present and absent marks update counter immediately; remaining periods stay unmarked')
tap('Overview')
find('76 / 102 classes')
adb('shell', 'am', 'force-stop', 'in.attend.app')
adb('shell', 'am', 'start', '-n', 'in.attend.app/.MainActivity')
find('76 / 102 classes')
shot('overview.png')
record_step('Recorded periods persist through process restart without forced ERP navigation')
tap('Calendar')
choose('Friday, October 23, 2026')
tap('Plan absence')
find(prefix='Absence planned for period')
tap('Forecast')
find('1 planned missed periods')
comparison = find(prefix='With your plan:').get('text')
rates = [float(v) for v in re.findall(r'(\d+\.\d+)%', comparison)]
assert len(rates) == 2 and rates[0] < rates[1], comparison
shot('forecast.png')
record_step('23 October planned absence lowers My plan relative to Attend every session')
tap('Calendar')
choose('Tuesday, October 20, 2026')
find(prefix='20 Oct 2026 · 0 scheduled')
choose('Thursday, October 22, 2026')
find(prefix='22 Oct 2026 · 4 scheduled')
record_step('Navratri closes 17–21 October and 22 October resumes four classes')
tap('Overview')
scroll_to('Connect college ERP')
find('Connect ERP')
find('Enter counts instead')
find('Import this page')
find(prefix='Sign in below.')
shot('erp-login.png')
tap('Attendance')
find(prefix='No attendance link found')
tap('Enter counts instead')
find('Starting attendance')
tap('Cancel')
record_step('ERP sign-in exposes import instructions, actionable missing-link status and count-entry fallback')
result = {'passed': len(steps), 'steps': steps, 'real_student_import_verified': False, 'password_manager_save_tested': False}
(out / 'smoke-results.json').write_text(json.dumps(result, indent=2)+'\n')
print(json.dumps(result, indent=2))
