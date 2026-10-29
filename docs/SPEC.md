# Attendance Predictor v0.1

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
