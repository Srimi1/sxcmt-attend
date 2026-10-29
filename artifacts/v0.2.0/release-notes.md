Attend 0.2.0 makes student-entered attendance the main workflow. ERP remains optional.

## What changed

- Mark completed periods Present or Absent. Unmarked periods stay unknown; edits replace earlier marks instead of counting twice.
- Enter starting attended / held totals without ERP. Counters and forecasts update from your period log, and records persist offline.
- Plan future period absences and see their effect in the default My plan forecast, alongside an attend-all comparison.
- Include the college-confirmed Navratri closure, 17–21 October 2026, with reopening on 22 October. Closures and cancellations remove held sessions.
- Use a simpler home screen and daily calendar, with an expandable month view, clear saved feedback and editable calendar changes.
- Keep ERP session cookies on-device, expose Android password-manager Autofill and remove forced ERP navigation when reopening Attend.
- Discover actual portal links, reread dynamic reports, explain unsupported layouts and offer manual count entry. Dismissing an import preview pauses automatic rereads until navigation or explicit retry.

## Install and try

Download **Attend-0.2.0-debug.apk** from Assets. Requires Android 8.0 or newer. Install it as an update to 0.1.0; it uses the same signing certificate and preserves existing application data.

Open Calendar and mark completed periods Present / Absent. For 23 October, use Plan absence, then open Forecast to compare My plan with attending every session. Use Add starting counts if you already know your total attendance.

## ERP acceptance still pending

Authenticated ERP import has not been verified against a real student report. Credential saving uses your phone's enabled password manager; it is not a separate app password vault. The college can expire its server session. No student credentials were entered during development.

[Verification details](https://github.com/Srimi1/sxcmt-attend/releases/download/v0.2.0-apk/VERIFICATION-v0.2.md).

This is an Android test prerelease, not a Play Store release. The accompanying checksum file verifies the APK download.
