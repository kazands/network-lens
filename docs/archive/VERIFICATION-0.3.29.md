# Network Lens 0.3.29 verification

Prepared on 2026-11-19. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **57**.

## Reviewed changes

More qualified product matches, separation of software descriptions from hardware,
MAC-source-aware address grouping, fairer diagnostic retention and identifier
redaction, range-based scan budgets, and explicit handling of Android installation
blocks. See [changes](../CHANGES.md) and [current limits](../STATUS.md).

The latest fresh independent Deep review approved the implementation with no
substantive findings after the earlier MAC-source issues were corrected. Release
preparation changed version metadata and documentation. All 50 APK payload entries
other than the manifest match the reviewed candidate; all 51 non-signature entries
match between the development and public builds.

## Passed checks

- The implementation passed all 51 host suites and 29 Python tests. The final
  independent review reran eight focused host suites after the MAC-source fixes.
  The full host suite was not rerun solely for release version/documentation changes.
- Versioned development and public-signed builds passed, including packaged
  permission/security audits and the pinned signing-identity check.
- The instrumentation build passed. The exact versioned development APK passed
  all six API 37 modes below in one run, with installed app/test hashes verified.
- The exact public APK passed 23 in-place upgrade/launch checks from public
  0.3.28: certificates, installed bytes, versions, UID, existing runtime grants,
  launch and no automatic scan. Only an isolated emulator was operated.
- Recognition coverage, staged repository hygiene, project documentation links
  and whitespace checks passed. No GitHub CI was added.

| Android mode | Assertions | Result |
| --- | ---: | --- |
| device-identities | 1239 | PASS |
| grouping | 33 | PASS |
| recognition-report | 235 | PASS |
| scan-ownership | 15 | PASS |
| scan-efficiency | 22 | PASS |
| app-updates | 553 | PASS |

Total: **2097 Android assertions across six modes**.

## Artifact identity

- Public APK SHA-256: `cfa6ec2538d98035c55d466298dbf3e324cb5cc775f14aeb4fc3ee36eca05295`.
- Development APK SHA-256: `0efd9cdc72c97415bbcad46cc9909ee697ada0e1933468f13899b1356f440ab0`.
- Instrumentation APK SHA-256: `5cf07cdba119b0e08003daf29bddd9fe416e0ad316f17916759eb165b8dd8422`.
- Reviewed candidate SHA-256: `e505a5d0d7fe1479c9cfd2a5662f5fb7fcc7cfce9d2bf2feb03dfc32e585a7c6`.

The public certificate remains pinned to
`5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
The source archive identifies the merged Git commit/tree, per-file hashes and
separately listed public regeneration inputs. Credentials, household data and
local caches are excluded. Asset checksums are supplied in `SHA256SUMS`.

## Limits and required physical checks

The reported Google Play Protect classification remains unresolved. Installation
block handling is tested with synthetic authenticated installer callbacks; it does
not establish Google clearance or explain the classification. Keep Play Protect
enabled. No claim is made that this release removes its warning.

Real /22 completion time, Wi-Fi power-save behavior and affected-device recognition
still require physical testing. The supported sweep budget scales from 55 seconds
for a /24 to 70 for a /23 and 100 for a /22, while preserving cancellation and time
for metadata. Synthetic timing and incomplete-work tests do not guarantee complete
discovery. Larger automatic networks still use the documented service-only fallback.

The Apple identity-service response-read failure is not repaired by catalogue
matches. Unknown or contradictory evidence remains unresolved. Grouped addresses
are not a verified physical inventory. Troubleshooting reports remain bounded and
count omissions; no claim is made that every response field is retained.

No phone was operated. Physical firmware compatibility, multicast reliability,
TalkBack, low-memory performance and API 26/36 were not retested. The signed upgrade
check uses Android installation on an emulator; live in-app internet download and
physical installation remain separate checks. No new permission, network probe,
background scan, saved history or cloud recognition was introduced.
