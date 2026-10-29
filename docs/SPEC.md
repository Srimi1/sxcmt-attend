# Attendance Predictor

## Self-service update — v0.3.0, 29 October 2026

ERP is removed entirely: no WebView, parser, import, stored reports or INTERNET permission. The app must never depend on ERP. Where earlier sections mention ERP, they are superseded.

- Baseline: the student's row from the college monthly sheet — cumulative attended, cumulative held, covered-through date, optional month Σ/held and per-subject counts. Saved subjects and month totals persist.
- Marks: completed periods are Present / Absent / unmarked. Marks on or before the sheet date are superseded by the sheet. Unmarked periods never count.
- Cumulative = (sheet attended + P) / (sheet held + P + A). Minimum 75%, editable. Monthly = sheet month total (for the sheet's month) + marks in that month; monthly percentages are never averaged into the cumulative figure.
- Activity hours: hours × lectures-per-hour (default 1) are added to attended only, capped at held (≤100%); excess is reported as unused. Hours-to-target = ceil(target × held) − attended, divided by lectures-per-hour, rounded up.
- Today screen marks each period with one tap (P / A, tap again to clear) plus "mark remaining present".
- Old 0.2.x state decodes; retired ERP keys are ignored and dropped on next save.
- Shared routine (InsForge): email-OTP sign-in; create class (join code) or join by code; members read routine versions; any member publishes a new append-only version; restore = republish. Active version = newest whose effective date has arrived. Only timetables, membership and sign-in email are stored online. Server validates timetables (Mon–Sat, periods 1–12, unique slots, ≤72 periods), rate-limits publishing (5/day/member, 30/day/class), class creation (3/day), joins (10/day), wrong codes (10/hour), and caps payloads at 32 KB with no control or invisible formatting characters. Classmates' versions need the student's approval before replacing their timetable; own versions apply directly. Creator can rotate the join code. Clients keep the latest 10 versions and skip any version that fails to decode.


## Publication corrections — v0.2.1, 29 October 2026

Historical marks remain visible and correctable after timetable edits or deletions, retaining their original subject snapshots. Older reporting dates remain readable without applying the forecast's ten-year limit to stored history. Validate combined totals before changing either persistent or sample state.

Explicitly saved manual starting counts take precedence over cached ERP totals until the next verified ERP import; preserve the cached reports for account isolation and history. Explain this choice when entering counts. Show available subject counts and distinguish the report date from the last successful ERP refresh timestamp on the dashboard.

## Student review update — v0.2, 20 October 2026

Student-entered period attendance is the main workflow. Completed periods accept Present / Absent / Clear; unmarked periods stay unknown. Future period absences affect planning only. Counters update immediately and marks persist offline. Optional starting counts and verified ERP totals establish a baseline; official refreshes supersede student marks on covered dates without double counting. Timetable edits preserve snapshots of already marked periods.

Use My plan as the default forecast and show its planned missed-period count and attend-all comparison. Planned absences also override expected attendance in the current-rate scenario. Holidays and cancellations remove held sessions rather than penalizing attendance. The college confirmed 17–21 October 2026 as the Navratri closure, reopening 22 October; include this once when upgrading older local state, while honoring subsequent edits/removal.

Keep ERP sessions on-device, expose Android Autofill, and keep refresh user-initiated so returning to the app shows attendance. Explain sign-in, opening an attendance report and importing it as separate stages. Discover actual profile/attendance links instead of guessing dashboard routes. Read dynamic visible content again periodically, and offer manual count entry when a layout is unsupported. Native password copying is not part of this update; real password-manager saving and authenticated ERP import still require phone verification.

The requirements below describe the original release. Where they conflict, this student review update takes precedence.

## Accepted behavior

Native Android 8+ application, written in Kotlin with Jetpack Compose / Material 3 and Room. Data stays on device. Deliver an installable test APK without a backend or separate app account.

Students enter their own credentials directly into the official HTTPS ERP portal. Automatically read their name, roll number, semester and available attendance after login. Never read, log or separately store passwords. Refresh on app open and manually. Retain valid imported records when offline or refresh fails. Logout removes session and personal data; accounts and semesters must not mix.

The dashboard distinguishes the report's attendance date from the refresh timestamp. Show overall attendance and available subject counts. Attendance equals attended / held; monthly percentages must not be averaged. Importing the same period replaces it. Cumulative snapshots must not be added to months they include. Reject partial overlaps and ambiguous report scope.

Seed the provided BBA IB timetable: Monday 5, Tuesday 3, Wednesday 3, Thursday 4, Friday 5 and Saturday 4 sessions; Sunday off. Include Placement Seminar, Activity and Friday's two IFS periods. Automatically exclude only 26 January, 15 August and 2 October. Allow closures, session cancellations and planned absences. Timetable is editable.

Forecast through a selected date using current cumulative attendance rate, all-attended, or planned absence scenarios. Default to current rate. Compute from the last report date, label unreported intervening days estimated, and stop at known semester teaching end. Otherwise state timetable continuation assumption. Show subject forecasts only with matched counts and timetable codes. Default target 75%, editable; recovery and absence allowance use exact thresholds, not display-rounded percentages.

## Validation

Sheet totals: 54, 86, 45; cumulative held 54, 140, 185. Anonymized example: 42, 76, 43 attended; cumulative 161/185 = 87.03%. September timetable has 102 scheduled sessions before closures; sheet's 45 held is authoritative and the gap is unexplained.

Test zero classes, threshold boundaries, recovery, duplicates, overlapping periods, account switching, timetable, national holidays, cancellations, session expiry, offline reopening, unknown layouts and logout.

## Live integration gate

An authenticated portal page has not been supplied or inspected. Public login page is verified. Implement a conservative label-based adapter and explicit first-import preview, but do not invent authenticated endpoints/selectors or claim the real import works. First-release ERP acceptance remains pending until an actual student signs in, imports identity and counts, and compares them with ERP. Synthetic fixtures and sample mode are insufficient evidence.
