# Attend 0.2.0 verification — 20 October 2026

Installable test APK: `artifacts/v0.2.0/Attend-0.2.0-debug.apk` (17,648,059 bytes). Package `in.attend.app`, Android 8.0+, version code 2, target API 36. APK Signature Scheme v2 verification passed. Signing certificate matches 0.1.0, allowing an in-place update. Room schema remains version 1; local state gains backward-compatible JSON fields.

SHA-256: `78af9418837f46cae4d4080599b1fb863a4672d75540c0ef793cd12bad20f304`.

## Results

- 45 core tests passed, no failures.
- 13 Android instrumented tests passed on Android 16 / API 36, no failures.
- 7 UI smoke scenarios passed on the final APK.
- Android lint passed with no errors; existing dependency/plugin update warnings remain.
- Core line coverage: 93.44%. Instruction coverage: 90.78%; the 80% gate passed.

Regression coverage includes the reported 23 October absence not affecting the current-rate forecast, exact student period counts, unknown/unmarked periods, repeated edits, future marks, official-report replacement without duplication, term end, Navratri closure and reopening, timetable snapshots, Room state persistence and migration. Real WebView tests cover multi-column profiles, hidden navigation links, excluding password/input values, and rejecting an import preview without repeatedly reopening it. The latter test intercepts a synthetic HTTPS report in the test harness; it does not access an authenticated student report.

## Screen checks

- Clean launch prioritizes student tracking and shows confirmed Navratri break.
- Manual starting counts save without any ERP login.
- Present and absent marks update counter immediately; remaining periods stay unmarked.
- Recorded periods persist through process restart without forced ERP navigation.
- 23 October planned absence lowers My plan relative to Attend every session.
- Navratri closes 17–21 October and 22 October resumes four classes.
- ERP sign-in exposes import instructions, actionable missing-link status and count-entry fallback.

The counter check starts from 75 / 100, marks one completed period present and another absent, and verifies 76 / 102 with three periods still unmarked. Restart preserves the count. Planning a missed period on 23 October lowers My plan relative to attending every session. Navratri dates have no sessions; 22 October has four.

## Remaining phone acceptance checks

Authenticated ERP import remains unverified. The app retains ERP cookies and exposes Android Autofill with credential hints; saving and reusing real credentials through a phone password manager has not been tested. ERP can expire its server session. No student credentials were requested, entered or stored during validation.

Self-reported attendance and manual starting totals are labelled separately from imported ERP records. Forecasts remain estimates. Unmarked periods do not become recorded attendance, and future planned absence does not count as completed absence.

## Reproduce

```sh
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk \
  ATTEND_BUILD_ROOT="$HOME/.cache/attend-v02-build" ./gradlew \
  --project-cache-dir "$HOME/.cache/attend-v02-gradle" \
  :core:test :core:koverVerify :core:koverXmlReport \
  :app:connectedDebugAndroidTest :app:lintDebug
```

Install the produced APK on a disposable emulator dated October 2026, then run:

```sh
python3 scripts/smoke_v02.py --serial emulator-5580 --allow-reset
```

The smoke script explicitly clears only the disposable emulator's Attend data. Do not use it on a student's phone.
