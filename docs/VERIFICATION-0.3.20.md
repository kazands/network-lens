# Network Lens 0.3.20 verification

Prepared on 2026-11-18. Android development preview for home-network compatibility testing.

## Identity and scope

- Application ID `io.github.kazands.networklens`; version **0.3.20 / code 48**.
- Android 8.0+ (API 26); target API 37. The public signing identity is unchanged.
- Scan startup and Stop share a lifecycle lock. Cancellation is terminal, including
  during startup; multicast reception is owned and released safely after a late acquisition.
- Automatic networks above 1,024 addresses retain their actual scope and service
  discovery. The omitted individual-address sweep remains explicit in Scan details.
  Custom oversized or out-of-network ranges remain errors.
- EAP HTML comments are scanned linearly; unfinished comments cannot authorize an
  identity request. Technical details preserve the complete request/logging explanation.
- Complete multicast DNS responses with TC set are accepted without relaxing
  packet framing, size, source-association or current-response requirements.
- Installer-session opening failures are recoverable and clean up sessions/callbacks.
- Protocol adapters, metadata plans, resource ownership and detached UI snapshots
  have dedicated boundaries and regressions. Existing recognition catalogues remain.
- No permission, background scanning, saved inventory, analytics, cloud recognition,
  device pairing or control is added. Scan data and preferences remain in memory.

## Review and implementation checks

A fresh independent Deep kaz-code-review covered all 35 implementation files and
approved the corrected changes with no substantive findings. Release preparation
then updated version metadata, documentation and download links. All reviewed
Kotlin source and test files remained unchanged. All 47 application payload entries
other than the Android manifest match the reviewed pre-versioning candidate.

All **42 Kotlin host suites** and **seven offline Python tests** passed during
release preparation. Pinned catalogue/provenance checks passed offline. Earlier
candidate checks, parser timings and review corrections are retained in
[implementation history](REVIEW-FOLLOWUP.md). Those older hashes and counts do not
represent the versioned APK below.

## Exact versioned artifacts

- Direct development, public-signed and instrumentation builds passed, including
  packaged permission/security audits.
- Development APK SHA-256: `741716ac4434097fa9c440638344dec811f81c0c72956f901a8c682b1e98c90e`.
- Public APK SHA-256: `847daeb1de24dfa8acbf51587d049f64a9c263b2c3cffc2c8260f7a75dda728a`.
- Instrumentation APK SHA-256: `3361807e28fca5ca810bcfc1384717552ab5b06a05540bd3287b5cec43b34d0e`.
- All **48 non-signature payload entries** match between development and public APKs.
- **1,250 API 37 checks passed** across 10 modes:
  permission (21), scan-ownership (15), app-updates (146), device-identities (698), local-metadata (60), scan-efficiency (22), session-only (39), engine-upgrade (17), resolver-upgrade (21), service-retries (211).
- **183 API 26 checks passed** across 4 modes:
  scan-ownership (15), app-updates (146), engine-upgrade (17), resolver-upgrade (5).
- Both platform runs verified the exact installed application and instrumentation
  hashes. An initial API 37 emulator stalled before testing and was restarted;
  that startup is not counted as a test pass.
- All **116 isolated signing assertions** passed using generated fixture keys.
- **23 signed in-place update/launch checks passed** from the original public
  0.3.19 APK to the exact public 0.3.20 APK on an isolated API 37 emulator.
  The signing certificate and installed bytes matched; the UID and all three
  existing runtime grants were retained. Launch waited for a user-started scan.
  This check used ADB installation, not a live GitHub download through the app.

Public signing certificate SHA-256:
`5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

The release assets contain the signed APK, corresponding tagged project source
with public regeneration inputs, these verification notes and SHA256SUMS. Upstream
licenses/provenance remain included. Signing credentials, local tools, household
inventories and personal captures are excluded. CI was deliberately excluded from
this work; local verification is reported instead of an automated-CI claim.

## Compatibility and manual testing

This release improves lifecycle safety and protocol handling. It does not establish
that every previously unavailable Samsung, Apple, Spotify, VSSL, HomeKit or other
identity response now succeeds. Missing model, manufacturer and MAC values remain
unknown. A device must respond in the current scan before it is shown as present.

Physical-phone recognition, physical TalkBack, cold startup, battery behaviour,
all device/firmware combinations, VLANs and Thread meshes remain separate checks.
The exact release was tested on isolated emulators, not a household phone. Sleeping,
silent, isolated or credential-gated devices may remain unnamed; supported discovery
and metadata methods still have bounded work and deadlines. Skipped address sweeps
and unresolved service/address lookups preserve incomplete coverage internally.

For physical testing, install the signed APK as an update, start a fresh foreground
scan, compare reported identities with known devices, and check Stop, leaving the
app and network changes. Share only redacted model/service observations; omit
personal names, addresses, serials and raw household captures. See
[baseline procedure](RECOGNITION-BASELINE.md) and [development status](STATUS.md).

Google Play distribution and a verified App Bundle pipeline remain future work.
