Attend 0.4.0 makes the app fit each student, starting with present by default.

## Changes

- Present by default: finished periods count as present on their own. Tap A when you miss one, or No class when the teacher didn't take it. Counting starts the day you turn it on, a period only counts once it has ended, and planned leave counts as absent. Earlier unchecked days stay unmarked, never guessed.
- No class on every period: removes it from the held count and can be undone.
- Quick setup on first open (once for existing users): minimum %, how you mark, subjects you take, and a daily reminder. Change any of it later in Settings.
- Mark every period is still available. Switching to it keeps everything already counted.
- Subjects you take: untick electives or groups that aren't yours; they're hidden on your phone and never counted.
- Daily reminder: "Any absences today?" at your chosen time on teaching days. Works offline.
- Fixes: the period list no longer jumps on the first mark of the month, saved data no longer flashes default values on start, and clearer wording at exactly the minimum.

## Install

Install **Attend-0.4.0-debug.apk**. Requires Android 8.0 or newer. Version code 5 and the same signing certificate as earlier test APKs, so it updates in place and keeps your data. Allow notifications if you want the reminder.

## Verification and remaining checks

42 core tests (80% coverage gate), 13 Android instrumented tests on an API 31 emulator, and Android lint with 0 issues passed. On the emulator: setup, present-by-default counting, A, No class and undo, switching marking style, hidden subjects, light and dark mode, and the reminder firing at its set time.

Still to check on a real phone: that the sign-in code email arrives for the shared routine, the notification permission prompt on Android 13 and newer, and behaviour on the newest Android version.
