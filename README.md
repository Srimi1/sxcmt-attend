# Attend

An Android attendance planner for SXCMT / Xavier University, Patna. Native Kotlin, Compose Material 3, Room and a separately tested JVM calculation module. Requires Android 8.0 or newer.

[Download Attend 0.4.0](https://github.com/Srimi1/sxcmt-attend/releases/download/v0.4.0/Attend-0.4.0-debug.apk) · [App preview](artifacts/v0.4.0/today-present-by-default.png) · [Release notes](artifacts/v0.4.0/release-notes.md)

## Current status

Version 0.4.0 makes the app fit each student. A one-time setup asks for your minimum, how you mark, the subjects you take and a reminder time; all of it can be changed later in Settings and stays on your phone.

- **Present by default** (recommended): finished periods count as present on their own. Tap **A** when you miss one, or **No class** when it wasn't held. Counting starts the day you turn it on, a period only counts after it ends, and planned leave counts as absent. Days before that stay unmarked rather than guessed.
- **Mark every period**: only periods you tap count. Switching to this keeps everything already counted, as saved marks.
- **Subjects you take**: untick electives or groups that aren't yours; they're hidden on your phone and never counted.
- **Daily reminder**: an offline "Any absences today?" notification at your chosen time, on teaching days only.

Version 0.3.0 removes the college ERP integration completely. The ERP's numbers were not reliable and its live import was never verified, so the app no longer opens, reads or depends on the ERP in any way. Attendance is self-service:

- **Sheet numbers** — copy your row from the latest monthly college sheet: cumulative attended (LAST ATT + this month's Σ), cumulative held, the date it covers, and optionally that month's Σ and per-subject counts.
- **P / A per period** — the Today screen lists today's periods with one-tap **P** and **A** chips and *Mark remaining present*. Calendar handles other days, holidays, cancellations and planned leave.
- **Monthly and cumulative** — cumulative = (sheet attended + your P) / (sheet held + your P + A) and is checked against the 75% minimum. The month figure uses the sheet's month total plus your marks.
- **Activity hours** — extracurricular hours count as attended lectures (1 hour = 1 lecture by default, adjustable in Settings). They raise the attended side only: 360 / 600 (60%) + 30 h = 390 / 600 (65%). Credit cannot go above 100%; any excess is shown as unused.
- When the next sheet arrives, enter its numbers. The college's count replaces your marks up to that date, so nothing is counted twice.

Upgrading from 0.2.x keeps marks, starting counts, timetable and calendar changes. Saved ERP reports are dropped; if you relied on them, enter your sheet numbers once.

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

1. Install the APK. Tap **Enter sheet numbers** and copy your cumulative attended / held and the sheet's end date. Check the preview percentage matches the sheet's Cum_% column.
2. Each teaching day, check the Today screen. With present by default, finished periods already count; tap **A** for any you missed or **No class** if it wasn't held. With mark every period, tap **P** or **A** for each one. Tap a chip again to clear it.
3. Add extracurricular hours in **Activity**. The dashboard shows class-only and with-activity percentages separately.
4. For future dates, plan absences or add holidays, leave or cancellations in Calendar. **Forecast** compares your plan with attending everything.

## Counting rules

- The supplied timetable contains 24 sessions per week (5/3/3/4/5/4), including Placement Seminar and Activity. Both Friday IFS periods are separate sessions.
- Sundays, three national dates (26 January, 15 August, 2 October), and the college-confirmed 17–21 October 2026 Navratri closure are excluded. Other festivals require confirmed college closure dates. Saved calendar changes can be edited or removed.
- The college sheet's held counts are authoritative. September's scheduled 102 versus recorded 45 is not explained by the three sheets; no inferred holidays or attendance credits are fabricated.
- Cumulative attendance uses combined integer counts. The target is adjustable to two decimal places; recovery and absence limits are calculated without display rounding.
- Subject estimates require complete imported count coverage across the combined reporting periods and a matching timetable subject. Unknown labels stay unmapped rather than being assigned to another subject.

## Shared class routine

Settings → **Class routine** lets one classmate create the class from their timetable and share a 10-character join code. Everyone who joins gets the same timetable, so nobody types it twice.

- Sign-in is a 6-digit code emailed to you; there is no password.
- Any member can edit the weekly timetable; saving publishes a new version for the class. Versions are never edited or deleted, so a bad edit is undone with **Restore for class** in Version history.
- A classmate's change never replaces your timetable silently. The app shows it and asks **Use it** or **Keep mine**; your own changes apply straight away.
- The class creator can **Change join code** if the code leaks; existing members stay.
- The app refreshes the routine when it opens and on **Check updates**, keeping the latest 10 versions. Offline, the saved routine keeps working.
- Periods you already marked keep the subject they had when marked, so timetable changes never rewrite your history.

Backend: InsForge project `attend-routine` (ap-southeast). Schema and access rules are in `migrations/`. Clients read only their own classes and memberships; history and every write go through validated database functions. Limits: timetables ≤ 72 periods and 32 KB with no hidden/bidi characters; 3 new classes and 10 joins per user a day; 10 wrong join codes an hour; 5 publishes per member and 30 per class a day. Limits are not reset by deleting your class data. `app/routine.properties` holds the public base URL and anon key. The project admin key lives only in `.insforge/project.json`, which is git-ignored and must never be shared or bundled.

## Privacy

Attendance never leaves the phone: sheet numbers, P/A marks and activity hours are stored only in the app's Room database, and app cloud/device backups are excluded. The INTERNET permission is used only for the shared routine. Online, the backend stores your email (for sign-in), which classes you joined, and the timetables you publish. No analytics.

**Clear my attendance data** deletes everything on the phone and signs out. **Delete my class-sharing data** removes you from every class and detaches your account from routine history.

## Source map

- `core/`: counts, activity credit, monthly buckets, timetable/calendar and forecast math, with tests.
- `app/`: Android UI, Room persistence and the routine REST client (`data/RoutineClient.kt`).
- `migrations/`: backend schema, row-level security and routine functions.
- `docs/SPEC.md`: accepted behavior.
- `scripts/`: UI smoke scripts for 0.1/0.2; they reference the removed ERP screens and need rewriting for 0.3.

No classmates' names or full attendance sheets are bundled into the app.
