# Attend

An Android attendance planner for SXCMT / Xavier University, Patna. Native Kotlin, Compose Material 3, Room and a separately tested JVM calculation module. Requires Android 8.0 or newer.

[Download Attend 0.2.1](https://github.com/Srimi1/sxcmt-attend/releases/download/v0.2.1/Attend-0.2.1-debug.apk) · [App preview](artifacts/v0.2.1/overview.png) · [Verification results](docs/VERIFICATION-v0.2.1.md)

## Current status

Version 0.2.1 publishes the full student-tracking update with fixes for older reporting dates, historical timetable edits, manual totals overriding an older ERP import, and the dashboard's subject counts and refresh timestamp. It can update either earlier test APK while preserving device data.

Version 0.2 puts student attendance tracking first. Mark each completed period present or absent, keep unmarked periods unknown, optionally enter starting totals, and plan future absences. Counters and forecasts update from your entries without requiring ERP. Entries retain timetable snapshots so later timetable edits cannot erase recorded classes. The college-confirmed Navratri closure is 17–21 October 2026; teaching resumes 22 October.

The home screen offers attendance logging, starting counts, forecasts and optional ERP access. Calendar separates actual period marks from future plans; My plan is the default forecast, with an attend-all comparison. Closures and cancellations remove held classes; planned absences remove expected attendances. Sample data remains separate and temporary.

**Live ERP integration is a prototype, not a verified integration.** The public sign-in page is known; the signed-in profile and attendance markup are not. A conservative label-based adapter can read supported visible tables, but a real student sign-in and count comparison are still required. Unsupported or ambiguous layouts produce a visible error and leave saved attendance unchanged. Do not describe a successful synthetic parser test as a working live ERP import.

## Build

Install JDK 17 and Android SDK platform 36 / build-tools 36.0.0. Set `ANDROID_HOME` or create ignored `local.properties` containing your `sdk.dir`.

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
./gradlew :core:koverHtmlReport
```

If the workspace is in a cloud-synced folder, keep generated output outside it to avoid duplicated build files:

```sh
ATTEND_BUILD_ROOT="$HOME/.cache/attend-build" ./gradlew \
  --project-cache-dir "$HOME/.cache/attend-gradle-project" \
  :core:test :core:koverVerify :app:assembleDebug :app:lintDebug
```

With that override, APK output is under `$HOME/.cache/attend-build/app/outputs/apk/debug/`.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

The Gradle wrapper pins Gradle 8.13 and verifies the distribution SHA-256. Maven dependencies use fixed versions and checked-in dependency locks. Google Maven and Maven Central are the app's dependency repositories.

## Use

1. Install the APK and use **Log attendance**. For past dates or today, mark each period **Present** or **Absent**. Marks save on the device and update the counter. A cleared mark becomes unknown.
2. Optionally use **Add starting counts** for known attended / held totals and their reporting date. These totals take precedence over saved ERP reports until the next verified import. Log periods after that date; entries covered by those totals are never added again.
3. For future dates, select **Plan absence** for a period or add a holiday, leave or cancellation. **See forecast impact** opens My plan, which compares planned attendance with attending every session.
4. Optional ERP: choose **Connect college ERP**, sign in on the official page and open your profile / attendance report. Profile and Attendance buttons follow actual discovered portal links; **Import this page** reads the visible report. Unsupported layouts show a reason and offer **Enter counts instead**.
5. Before first ERP import, compare identity, reporting date, scope and counts. Save only if they match. A later official report supersedes covered student marks. ERP refresh is user-initiated and never hijacks app reopening.

ERP cookies are flushed to device storage, and WebView exposes Android Autofill with username/password hints. Use the phone's password-manager save prompt; the app does not maintain a separate password vault. Server-side ERP expiry may still require a fresh sign-in. Successful credential saving with a real password manager remains a phone acceptance check.

**Unsupported layout:** no records are imported. Navigate to a report with labelled identity, explicit reporting dates and attended/held counts. The adapter may need a targeted update after an authenticated page is inspected. Passwords or a session cookie must never be shared to do that work.

**Offline:** student marks, starting totals, plans and previous imports remain usable. A report date and a fetch timestamp represent different things. Unmarked or future sessions are never promoted to recorded attendance.

## Counting rules

- The supplied timetable contains 24 sessions per week (5/3/3/4/5/4), including Placement Seminar and Activity. Both Friday IFS periods are separate sessions.
- Sundays, three national dates (26 January, 15 August, 2 October), and the college-confirmed 17–21 October 2026 Navratri closure are excluded. Other festivals require confirmed college closure dates. Saved calendar changes can be edited or removed.
- Official held counts are authoritative. September's scheduled 102 versus recorded 45 is not explained by the three sheets; no inferred holidays or attendance credits are fabricated.
- Cumulative attendance uses combined integer counts. The target is adjustable to two decimal places; recovery and absence limits are calculated without display rounding.
- Subject estimates require complete imported count coverage across the combined reporting periods and a matching timetable subject. Unknown labels stay unmapped rather than being assigned to another subject.

## Privacy and connection boundaries

No analytics, backend, credential vault or password interception. Extraction never reads password inputs, cookies, tokens or form submissions. The only allowed extraction origin is `https://erp.sxcpatna.edu.in` on its standard HTTPS port. File/content access, mixed content, third-party cookies and WebView debugging are disabled. TLS errors stop the connection. Session data lives in the app's WebView sandbox; app cloud/device backups are excluded. Logout clears session and local personal records.

## Source map

- `core/`: counts, report reconciliation, timetable/calendar, forecast math, portal policy and conservative parser tests.
- `app/`: Android UI, Room persistence, ERP WebView and visible-page capture.
- `docs/SPEC.md`: accepted behavior and remaining live integration gate.

No classmates' names or full attendance sheets are bundled into the app.
