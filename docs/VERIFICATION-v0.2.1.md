# Attend 0.2.1 verification — 29 October 2026

Package `in.attend.app`, version `0.2.1`, version code 3, minimum API 26, target API 36. The test APK is distributed through the [v0.2.1 release](https://github.com/Srimi1/sxcmt-attend/releases/tag/v0.2.1), with a SHA-256 checksum alongside it. The complete source is included in the release tag.

APK: `artifacts/v0.2.1/Attend-0.2.1-debug.apk`, 17,592,998 bytes.

SHA-256: `969300b1f407fc49d9aff5b482c5cbdc4ed1230dcb725d066650820935b51590`.

APK Signature Scheme v2 verification passed. The signing certificate matches the earlier test APK, allowing an in-place update. Room schema remains version 1, with backward-compatible optional JSON state fields.

## Automated checks

- 47 Kotlin core tests passed with no failures.
- 16 Android instrumented tests passed with no failures on an Android 16 / API 36 disposable emulator.
- Android lint passed with no errors. Existing plugin/dependency upgrade warnings remain.
- Core line coverage: 93.66%. Instruction coverage: 90.87%. The 80% instruction coverage gate passed.
- Standards/security and spec reviews found no unresolved material issues after the fixes.
- All 7 UI smoke scenarios passed on the final APK: clean launch, manual starting counts, Present/Absent counters, persistence after restart, planned-absence forecast impact, confirmed closure/reopening, and ERP instructions with manual fallback.

Added regression coverage checks starting totals and marks older than ten years, visible historical periods after timetable edits/deletions, snapshot preservation while correcting marks, manual totals overriding stale ERP counts until a verified refresh, and invalid combined counts leaving stored data unchanged. The dashboard again shows available subject counts and the last successful ERP refresh timestamp.

Screenshots and machine-readable results are saved under `artifacts/v0.2.1/`. Smoke data is synthetic; no student identity or credentials are included.

## Remaining phone checks

Authenticated ERP import remains unverified against a real student's report. Android Autofill hints and on-device ERP cookies are implemented, but credential saving and reuse with a real phone password manager remain untested. ERP can expire its server session. No student credentials were entered during validation.

## Reproduce

Install JDK 17 and Android SDK platform 36 / build-tools 36.0.0, then attach a disposable API 36 emulator:

```sh
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk \
  ATTEND_BUILD_ROOT="$HOME/.cache/attend-build" ./gradlew \
  --project-cache-dir "$HOME/.cache/attend-gradle-project" \
  :core:test :core:koverVerify :core:koverXmlReport \
  :app:assembleDebug :app:connectedDebugAndroidTest :app:lintDebug
```

The calendar smoke scenario uses 20 October 2026 as its device date so that 23 October is a future planning date. Set that date only on the disposable emulator. After the instrumented suite, install the produced APK again because Gradle uninstalls its test packages:

```sh
adb -s emulator-5580 install -r "$HOME/.cache/attend-build/app/outputs/apk/debug/app-debug.apk"
python3 scripts/smoke_v02.py --serial emulator-5580 \
  --output-dir artifacts/v0.2.1 --allow-reset
```

The smoke script clears Attend data on the specified emulator. Do not run it on a student's phone. Historical v0.2.0 verification remains in `docs/VERIFICATION-v0.2.md`; those results and its checksum describe the earlier APK-only prerelease.
