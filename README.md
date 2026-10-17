# Attend

An Android attendance planner for SXCMT / Xavier University, Patna. Native Kotlin, Compose Material 3, Room and a separately tested JVM calculation module. Requires Android 8.0 or newer.

[Download the Android test APK](https://github.com/Srimi1/sxcmt-attend/raw/refs/heads/main/artifacts/Attend-0.1.0-debug.apk) · [App preview](artifacts/overview.png) · [Verification results](docs/VERIFICATION.md)

## Current status

Attendance calculation, supplied BBA IB timetable, national holiday calendar, editable closures/cancellations/absences, forecast scenarios, target settings and local persistence are implemented. The optional sample preview uses anonymized sheet counts in memory, separately from real records.

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

1. Install the APK, open Attend, and choose **Connect college ERP**.
2. Sign in on the official ERP page. Credentials are entered into the portal, not a native app form.
3. Open your own profile and attendance report. Attend follows visible profile/attendance navigation where available; **Read page** retries extraction after dynamic page updates.
4. On first import, compare the identity, reporting date, scope, attended and held totals with ERP. Save only if they match. Later refreshes auto-save only the same verified layout and account.
5. Set teaching end date when known. Use Calendar for planned absences, closures or cancelled periods, and Forecast to compare scenarios.

**Unsupported layout:** no records are imported. Navigate to a report with labelled identity, explicit reporting dates and attended/held counts. The adapter may need a targeted update after an authenticated page is inspected. Passwords or a session cookie must never be shared to do that work.

**Offline:** close a failed refresh to view the last saved attendance. A report date and a fetch timestamp represent different things. No future timetable session is silently promoted to recorded attendance.

## Counting rules

- The supplied timetable contains 24 sessions per week (5/3/3/4/5/4), including Placement Seminar and Activity. Both Friday IFS periods are separate sessions.
- Sundays and only the three national dates (26 January, 15 August, 2 October) are automatic exclusions. Other festivals require a college closure entry.
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
