Attend 0.2.1 publishes the complete student-attendance update and fixes issues found during publication review.

## Changes

- Log completed periods Present or Absent, clear mistakes, and leave unknown periods uncounted.
- Enter starting totals without ERP and plan future absences in Calendar. My plan compares your planned attendance with attending every session.
- Preserve historical period rows and original subjects after editing or deleting timetable sessions.
- Keep older reporting dates readable and reject invalid combined totals before changing saved or sample state.
- Let manual starting counts override an older saved ERP report until the next verified import. Existing ERP history stays on the device.
- Restore subject attendance and the last successful ERP refresh time on the dashboard.
- Include the college-confirmed 17–21 October 2026 Navratri closure, optional ERP session persistence, Android Autofill hints, portal link discovery, and clearer import errors.

## Install

Download **Attend-0.2.1-debug.apk** from Assets. Requires Android 8.0 or newer. Version code 3 and the existing signing certificate allow an update from either earlier test APK while preserving local data.

Source code, screenshots, checksums and verification details are included in this release's source tag. This is an Android test prerelease.

## Verification and remaining checks

47 core tests, 16 Android instrumented tests and 7 UI smoke scenarios passed. Android lint and the 80% coverage gate passed. See [verification details](https://github.com/Srimi1/sxcmt-attend/blob/v0.2.1/docs/VERIFICATION-v0.2.1.md).

Authenticated ERP import and saving real credentials through a phone password manager still require student acceptance testing. No student credentials were entered during validation.
