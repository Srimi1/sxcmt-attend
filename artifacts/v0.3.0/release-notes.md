Attend 0.3.0 removes the college ERP completely and makes attendance self-service.

## Changes

- No ERP: the app no longer opens, reads or depends on the college ERP. Saved ERP reports from 0.2.x are dropped; enter your sheet numbers once.
- Sheet numbers: copy cumulative attended / held from the monthly college sheet, plus optional month totals and per-subject counts. The preview should match the sheet's Cum_% column.
- One-tap P / A for each period on the Today screen, plus "Mark remaining present".
- Cumulative and monthly attendance, checked against the 75% minimum, with classes you can still miss or must attend.
- Activity hours: each hour counts as one attended lecture (adjustable). They raise attended only, capped at 100%. 360 / 600 + 30 h = 390 / 600 (65%).
- Shared class routine: sign in with an emailed code, create a class or join by code. Timetable edits publish a new version; classmates' changes wait for your approval. Only timetables, class membership and your sign-in email are stored online. Attendance never leaves the phone.
- New look: white and black with dark purple and dark blue accents; green and red only for on-track or below the minimum. Light and dark mode.

## Install

Install **Attend-0.3.0-debug.apk**. Requires Android 8.0 or newer. Version code 4 and the same signing certificate as earlier test APKs, so it updates in place and keeps marks, timetable and calendar changes.

## Verification and remaining checks

34 core tests (80% coverage gate), 10 Android instrumented tests on an API 31 emulator, Android lint with 0 issues, and 36 live backend security checks passed. Sheet maths, marking, activity hours, class creation and classmate approval were walked through on the emulator.

Still to check on a real phone: that the sign-in code email arrives, and behaviour on the newest Android version. Full sign-in account deletion is not available in the app yet; "Delete my class-sharing data" removes you from classes and routine history.
