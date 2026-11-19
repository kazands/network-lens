# Network Lens 0.3.27 verification

Prepared on 2026-11-19. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **55**.

## Reviewed changes

Whole-network troubleshooting reports, retained diagnostic fields and lookup history,
privacy preview and temporary sharing, counts-only recognition summaries, developer
recognition-baseline/public-web trial tools, and stable device-details scrolling.
See [changes](CHANGES.md) and [report coverage](reference/TROUBLESHOOTING-REPORT.md).

A fresh independent Deep code review approved all 47 implementation files with no
substantive findings. Release preparation then updated version metadata and documentation.
All 50 APK payload entries other than the manifest match the reviewed candidate;
all 51 non-signature entries match between the development and public builds.

## Passed checks

- The final implementation passed all 50 host suites and 25 Python tests. The final
  review reran 394 focused host assertions, a real-parser masking reproduction,
  20 independent Python tests and whitespace checks. Host suites were not rerun
  solely for the release version/documentation changes.
- Versioned development, public-signed and Android instrumentation builds passed,
  including packaged permission/security audits.
- The release-signing harness passed 116 assertions with isolated fixture keys.
- The exact versioned development APK passed the API 37 matrix below; installed
  application and instrumentation APK hashes were verified.
- The exact public APK passed 23 in-place upgrade/launch checks from public 0.3.26:
  certificate, installed bytes, version, UID, runtime permissions, launch and no
  automatic scan. Only an isolated emulator was operated.
- Recognition coverage policy, staged repository hygiene, whitespace and project
  documentation links passed. Retained upstream documentation is excluded from
  the project link check.

| Android mode | Assertions | Result |
| --- | ---: | --- |
| recognition-report | 235 | PASS |
| detail-stability | 124 | PASS |
| local-metadata | 60 | PASS |
| service-retries | 211 | PASS |
| scan-details | 74 | PASS |
| app-updates | 502 | PASS |

Total: **1206 Android assertions across six modes**.

## Artifact identity

- Public APK SHA-256: `57d1e16bf46a79e6a641364b54547f8d83fb4f18c1b14f1c6be05bd3bad08ef3`.
- Development APK SHA-256: `3dbf0867bcd4a9064cfa7657a54b861b74a36b217c5662e8e1f422e35a9c8ef5`.
- Instrumentation APK SHA-256: `13427b8c0eb39de54917c268bc3925b0b5bb23c9831d7801111000962d4a9907`.

The public certificate remains pinned to
`5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
The release source archive identifies the merged Git commit/tree, per-file hashes
and separately listed public regeneration inputs. Credentials, household data and
local caches are excluded. Asset checksums are supplied in `SHA256SUMS`.

## Limits

These checks do not establish physical firmware compatibility, household recognition
accuracy, wireless multicast reliability, physical TalkBack or low-memory-phone
performance. API 26/36 were not rerun. Temporary sharing was tested through Android's
content resolver; third-party receiving-app compatibility remains unverified. The
public upgrade check used Android installation on an emulator; live in-app internet
download and physical installation were not exercised. No phone was operated.
No GitHub CI was added; verification was performed locally.

Reports expose retained evidence and explicit omissions; they do not capture every
response byte or identify silent devices. This release does not claim that reporting
improvements resolve discovery failures. Earlier evidence remains in [the archive](archive/README.md).
