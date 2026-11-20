# Network Lens 0.3.33 verification

Prepared on 2026-11-20. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **61**.

## Reviewed changes

Envisalink TPI checks now require an explicit per-device action after an integration warning. A fresh response schedules one requested check independently of the address sweep, including on service-only networks. Normal scans do not run the Envisalink identity probe. Existing scope, own-address, cancellation, deadline and tracked-socket protections remain in place.

Troubleshooting reports mask additional IPv6 literals and use a compressed name-matching index with a bounded per-report cache. Device-detail and troubleshooting-page clipboard copies are marked sensitive. SSDP retains per-sender processing and record budgets alongside global packet, record and time bounds; reached limits remain visible.

A fresh independent Deep review covered all 17 implementation files and approved the final changes with no substantive findings. All **54 host suites** and Python prechecks passed. A separate synthetic probe passed **3,000 seeded comparisons** against the previous case-insensitive token and overlap behavior. Focused host results include 70 troubleshooting-report assertions, 99 lifecycle checks and 38 Envisalink reader checks.

The reviewed implementation candidate passed 1,476 Android assertions across Envisalink, scan ownership and device identity modes. Those results predate release versioning. Release preparation changes version metadata and documentation only; the versioned checks below identify the new APK separately.

## Versioned checks

- Versioned development, public-signed and instrumentation builds passed, including packaged permission/security audits and pinned signing checks.
- All 51 non-manifest APK payload entries match the reviewed implementation candidate. All 52 non-signature entries match between development and public APKs.
- The exact versioned development APK passed the API 37 matrix below; installed app and instrumentation hashes were verified.
- The exact public APK passed 23 in-place upgrade and launch checks from public 0.3.32, including certificates, installed bytes, versions, UID, existing runtime grants and no automatic scan. Only an isolated emulator was operated.
- Recognition coverage, repository content, current documentation links and whitespace checks passed. No CI is configured or was added.
- The first emulator startup stalled before any application test began. That task-owned emulator was stopped. A fresh session booted normally and completed the entire matrix and signed upgrade checks; the emulator was then stopped.

| Android mode | Assertions | Result |
| --- | ---: | --- |
| envisalink | 90 | PASS |
| recognition-report | 243 | PASS |
| device-identities | 1371 | PASS |
| scan-ownership | 15 | PASS |
| detail-stability | 124 | PASS |
| app-updates | 553 | PASS |

Total: **2396 Android assertions across six modes**. Some modes overlap; this is not a count of unique scenarios.

## Artifact identity

- Public APK SHA-256: `26158b623d4c0bac3a81249de77ff9fe960f78ea8cf697076540d392c450b7bf`.
- Development APK SHA-256: `22ec1af614fe628fa01f31df9e2753e9b3b461813d0598185a48bafec33c8235`.
- Instrumentation APK SHA-256: `c1cf8b97951a02838a12cc8423393597f76edb75fd6a9d756296845ae52a6e78`.
- Reviewed candidate APK SHA-256: `970b9f5bb1a2cca3832e11f1cebb5da7274d2a517a7759d9575bbb8d3cae7176`.

The public certificate remains pinned to `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
The source archive identifies the merged Git commit/tree and per-file hashes, with separately listed public regeneration inputs and their notices. Credentials, household data and local caches are excluded. Public asset checksums are supplied in `SHA256SUMS`.

## Limits and required physical checks

Physical alarm-integration compatibility remains unverified. A receive-only TPI connection can briefly occupy the vendor's single-client integration service; explicit consent and a short connection do not remove that risk. Other discovery paths can still confirm ports explicitly advertised by a device.

Clipboard sensitivity suppresses previews where supported; it is not access control and does not prevent a receiving app from retaining pasted data. Report preparation performance and clipboard behavior need broader phone testing. Masking is data minimisation, not a guarantee of anonymisation; review reports before sharing.

No phone was operated. Real /22 timing, physical discovery accuracy, multicast reliability, TalkBack, low-memory performance and API 26/36 were not retested. Live in-app internet download and physical installation remain separate checks.

The reported Google Play Protect classification remains unresolved. Successful emulator installation does not establish Google clearance or explain the warning. Keep Play Protect enabled.

No new permissions, background scanning, saved inventory, cloud recognition or device-control commands are added. Synthetic tests do not establish complete discovery or recognition accuracy.
