# Network Lens 0.3.26 verification

Prepared on 2026-11-18. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **54**.

## Reviewed changes

Broader integration-family labels, recognition explanations, Ubiquiti/MikroTik
readers, eSCL service details, bounded recovery, IPv6 SSDP/admission improvements,
and smart-home/remote icons. Scanner advertisements and capabilities cannot
replace their host's identity. See [changes](../CHANGES.md) and [recognition](../RECOGNITION.md).

Deep code review, including a fresh independent reviewer, approved the complete
50-file implementation changeset without substantive findings. The release step
then changed the version and reorganised documentation. All 50 APK payload entries
other than the manifest match the reviewed candidate; all 51 non-signature entries
match between development and public builds.

## Passed checks

- The final implementation passed all 48 host suites and seven Python tests.
  Focused review reruns passed 296 recognition, 133 equipment and 652 retry checks.
- Direct versioned development, public-signed and instrumentation builds passed,
  including packaged permission/security audits.
- The release-signing harness passed 116 assertions with isolated fixture keys.
- The exact versioned development APK passed the API 37 matrix below. The runner
  verified installed application and instrumentation hashes.
- The exact public APK passed 23 in-place upgrade/launch checks from public 0.3.25:
  certificate, installed bytes, version, UID, runtime permissions, launch and no
  automatic scan. Only the isolated emulator was operated.
- Coverage inventory, staged repository hygiene and whitespace checks passed.
  All 243 relative links in staged project documentation resolved; retained
  upstream documentation was excluded from this link audit.

| Android mode | Assertions | Result |
| --- | ---: | --- |
| device-identities | 1086 | PASS |
| service-retries | 211 | PASS |
| local-metadata | 60 | PASS |
| scan-ownership | 15 | PASS |
| home-user | 61 | PASS |
| scan-details | 74 | PASS |
| app-updates | 502 | PASS |

Total: **2009 Android assertions across seven modes**.

## Artifact identity

- Public APK SHA-256: `a9c5d502e62d7d1ed6fbdcd749ba2a7cea262cefd45b1a4e293035a16461de62`.
- Development APK SHA-256: `2ad7c0b85d9e24f862dec18f301bf32dc84e346569ffe5c07bc572f46d9c3511`.
- Instrumentation APK SHA-256: `08d85d63de834859776394d41c3dd227e497cbfc512c378844fc1845b9dec4b0`.

The public certificate remains pinned to
`5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
The release source archive identifies the merged Git commit/tree, per-file hashes
and separately listed public regeneration inputs. Private keys, household data
and local caches are excluded. Asset checksums are supplied in `SHA256SUMS`.

## Limits

No physical firmware compatibility, household accuracy percentage, wireless
multicast reliability, physical TalkBack or low-memory-phone performance is
established by these synthetic checks. API 26/36 were not rerun for this release.
The public update check used Android installation on an emulator; live in-app
internet download and physical installation were not exercised. No phone was
operated. No GitHub CI was added; verification was performed locally.

Earlier candidate and release results remain in [the archive](README.md).
They are not substituted for this version's results.
