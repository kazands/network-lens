# Network Lens 0.3.28 verification

Prepared on 2026-11-19. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **56**.

## Reviewed changes

Compatible Apple board/product aliases, Brother printer and Chromecast model
reports, reviewed printer/speaker/plug categories, fewer false Apple conflicts,
accurate optional Cast check status, and prioritised bounded troubleshooting evidence.
See [changes](CHANGES.md) and [report coverage](reference/TROUBLESHOOTING-REPORT.md).

A fresh independent Deep review approved all 30 implementation files with no
substantive findings. Release preparation updated version metadata and documentation.
All 50 APK payload entries other than the manifest match the reviewed candidate;
all 51 non-signature entries match between development and public builds.
The [candidate record](VERIFICATION-RECOGNITION-EVIDENCE.md) identifies earlier
implementation checks separately from this release's exact artifact checks.

## Passed checks

- The implementation passed all 50 host suites and 29 Python tests. The latest
  independent review reran six focused suites (4913 assertions), four generator
  tests, generated-data consistency, recognition coverage and whitespace checks.
  Host suites were not rerun solely for release version/documentation changes.
- Versioned development and public-signed builds passed, including packaged
  permission/security audits and the pinned signing-identity check.
- Android instrumentation build passed. The exact versioned development APK
  passed the API 37 matrix below; installed application/test hashes were verified.
- The exact public APK passed 23 in-place upgrade/launch checks from public 0.3.27:
  certificate, installed bytes, version, UID, runtime grants, launch and no
  automatic scan. Only an isolated emulator was operated.
- Staged repository hygiene, recognition coverage, documentation links and
  whitespace checks passed. Retained upstream documentation is excluded from
  the project link check.

| Android mode | Assertions | Result |
| --- | ---: | --- |
| device-identities | 1100 | PASS |
| recog | 284 | PASS |
| google-cast | 158 | PASS |
| recognition-report | 235 | PASS |
| local-metadata | 60 | PASS |
| scan-details | 74 | PASS |

Total: **1911 Android assertions across six modes**.

## Artifact identity

- Public APK SHA-256: `b5fb4731c6ff72faf262a5212e9ee566b6e825b54ec9583e7aab02eb80cfcb55`.
- Development APK SHA-256: `8051f5a6cc18af3106ab92253a5914e535c112a960e5defb25bb69cd84227af5`.
- Instrumentation APK SHA-256: `34fd64f73308446f7c2cd20a1993ce137d827168b9eed3f126d0cc3d9c5b2577`.

The public certificate remains pinned to
`5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
The release source archive identifies the merged Git commit/tree, per-file hashes
and separately listed public regeneration inputs. Credentials, household data and
local caches are excluded. Asset checksums are supplied in `SHA256SUMS`.

## Limits

Synthetic checks do not establish household recognition accuracy or physical
firmware compatibility. Multicast reliability, physical TalkBack, low-memory
performance and API 26/36 were not retested. No phone was operated. The public
upgrade check uses Android installation on an emulator; live in-app internet
download and physical installation remain separate checks. No GitHub CI was added.

The Apple identity-service response-read failure is not repaired by catalogue
aliases. Only an independently reported supported model can help identify that
endpoint. Missing or ambiguous models remain unknown. Audio-zone groups are not
verified physical inventory. Reports remain bounded and explicitly count lost
records. No new discovery probe, permission, background work or cloud recognition
was added.
