# Verification — 17 October 2026

Built installable Android test package `artifacts/Attend-0.1.0-debug.apk` (17 MB). Application ID `in.attend.app`, version `0.1.0`, minimum API 26, target API 36. APK Signature Scheme v2 verification passed. The packaged copy's SHA-256 is recorded beside the APK and in `artifacts/verification.json`.

## Automated results

- 36 Kotlin core tests passed; no failures.
- 7 instrumented Android tests passed on the task's Android 16 / API 36 emulator; no failures.
- 8 UI smoke scenarios passed: clean onboarding, explicitly labelled sample data, planned absence, forecast, Friday's two IFS sessions, settings persistence across process restart, actual public ERP login page loading, and disconnect before a first import.
- Android lint passed with no errors. Remaining warnings recommend newer plugin/dependency versions.
- Core line coverage: 92.78%. The configured 80% instruction-coverage gate passed.

Tests cover weighted counts, 75% boundaries, recovery and absence limits, zero classes, report replacement and overlap rejection, month-to-date refresh, cumulative de-duplication, national holidays, closures/cancellations, term end, identity conflicts, optional identity metadata, offline Room reopening, logout and session deletion. Report dates remain distinct from fetch times and future estimates.

Independent standards and spec reviews found account/semester isolation, pending-count, duplicate-table, refresh replacement and failed-import retry defects. Fixes and regression cases were added. Portal identity binding now resets on any verified conflicting complete or partial identity before a later identity-free report can be considered.

## Remaining acceptance gate

**A real student's authenticated ERP import has not been verified.** No student credentials were requested or entered. The actual public ERP login loads in the app, but authenticated profile/attendance markup remains unknown. The adapter is a conservative label-based prototype requiring explicit identity, report scope/date and overall attended/held totals. Unsupported reports preserve saved records.

Next checkpoint: student installs the APK, signs in personally, opens their profile and attendance report, compares preview identity/date/counts against ERP, and reports whether import succeeds. A layout-specific update may be needed. Synthetic fixtures and sample data do not establish live portal support.

## Reproduce

```sh
JAVA_HOME=/path/to/jdk17 ATTEND_BUILD_ROOT="$HOME/.cache/attend-build" \
  ./gradlew --project-cache-dir "$HOME/.cache/attend-gradle-project" \
  :core:test :core:koverVerify :core:koverXmlReport \
  :app:assembleDebug :app:connectedDebugAndroidTest :app:lintDebug
```

An attached emulator/device is required for instrumented tests. The UI smoke script runs only on a disposable emulator and explicitly resets its Attend app data:

```sh
adb -s emulator-5554 install -r "$HOME/.cache/attend-build/app/outputs/apk/debug/app-debug.apk"
python3 scripts/smoke_android.py --serial emulator-5554 --allow-reset
```

Generated outputs were redirected outside the cloud-synced workspace after sync produced duplicate `.class` files. Project source, Room schema and dependency lockfiles remain in the workspace. GitHub publication is handled separately from these validation results; no store submission was performed.
