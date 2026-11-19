# Network Lens 0.3.19 verification

Prepared on 2026-11-17. Android development preview for physical compatibility testing.

## Identity and release scope

- Application ID `io.github.kazands.networklens`; version **0.3.19 / code 47**.
- Android 8.0+ (API 26); target API 37. The pinned public signing identity is unchanged.
- Receive-only Omada announcements and public EAP model lookup, Easy Smart discovery,
  Samsung TV identity, WiZ, Yeelight, Govee, LIFX and receive-only Tuya announcements.
- 348 pinned Home Assistant matcher rules across 122 mDNS types and 5,479 offline
  Matter vendor/product records. The existing free worldwide product catalogue remains.
- Independent Apple/Samsung/EAP identity opportunities, two validated same-peer metadata
  targets with shared deadlines, transport-security preservation and endpoint-fair queues.
- Physical identity and recognition explanation lead device details; raw source-labelled
  reports remain under Show technical details. Unnamed device and Scan finished/stopped
  describe presentation and lifecycle without changing underlying coverage/failure reporting.
- Fresh, foreground scans remain bound to the selected Wi-Fi/range. No pairing, device
  control, cloud recognition, saved scan history, analytics or new permission is introduced.

## Implementation verification before versioning

A fresh independent Deep kaz-code-review covered the full unpublished changes and
approved them with no substantive findings. All **41 host suites** passed, including
44 planning/fairness checks, 2,089 local metadata checks and 53 Matter checks.
A separate 100-trial concurrent queue stress check passed without losing accepted tasks.
Pinned HA projection and Matter table reproducibility checks passed offline.

The synthetic benchmark has 14 labelled cases: eight expected identifications,
eight correct titles, no misses, false-positive products or wrong primary categories.
These scores apply only to that corpus, not to household recognition accuracy.

Before the final transport-security guard, Android API 26 passed 244 selected
checks and API 37 passed 1,172 selected checks after a fresh emulator boot. The
first API 37 run failed under an Android System UI ANR dialog and is not a pass.
After the isolated guard change, the final earlier candidate passed 134 focused
API 37 checks, including metadata fallback, deadline finalization and permission
handling. See [planning evidence](RECOGNITION-PLANNING.md) for the exact candidate hashes.

Historical [pre-PR](PRE-PR-VERIFICATION.md) and [metadata follow-up](METADATA-FOLLOWUP.md)
records distinguish earlier candidates and retries from this release's exact artifacts.

## Versioned release artifacts and checks

- Direct development, public-signed and instrumentation builds passed; packaged
  permission/security audits passed. No permission was added.
- Development APK SHA-256: `b36d3a17c976179ea1160f5685ba9e986a3e8c5a2a270943d653db6bffffe777`.
- Public APK SHA-256: `bdf180b5e6342b9f3a0dc84438a3eefbf5c3f4c231fe2d40f5424a7005cd6134`.
- All **48 non-signature application payload entries** match between development
  and public-signed APKs.
- **958 versioned API 37 checks passed** across local-metadata (60),
  device-identities (696), app-updates (128), failure-reporting (53) and permission
  (21). The runner verified installed application and instrumentation hashes.
- All **116 isolated signing assertions** passed using generated fixture keys.
- **23 signed in-place update/launch checks passed** from the original public
  0.3.18 APK to the exact public 0.3.19 APK on an isolated API 37 emulator. Both
  certificate fingerprints and installed bytes matched; the UID and all three
  existing runtime grants were retained. Launch showed Network Lens waiting for
  a user-started scan. No physical device was operated.
- APK, complete tagged-source ZIP, verification notes and SHA256SUMS are release
  assets. The source archive retains public regeneration inputs and upstream
  licenses; private signing material and local evidence are excluded.
- The third-party Tuya MIT notice retains its upstream trailing blank line.
  Other staged whitespace checks and the repository prohibited-file/credential
  guard passed.

The installed public signing certificate SHA-256 remains
`5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
The signed-upgrade check above installs through ADB on an emulator; it is distinct
from a live public GitHub download through the app's updater. Any subsequent
published-updater check will be attached separately to the release.

## Compatibility limits

This release does not guarantee identification of sleeping, silent, isolated or
credential-gated devices. The original missing Samsung TV model was not conclusively
explained. VSSL, Spotify and Apple metadata can still be unavailable; valid Cast identity
responses can omit group information. Missing model, manufacturer and MAC stay unknown.
IPv4/IPv6 services need their own current evidence. No proprietary recognition database
or certificate-validation bypass is included.

The exact release has not been scanned on a physical phone or tested with physical
TalkBack, every model/firmware, VLAN topology or Thread mesh. Earlier desktop/phone
observations are useful compatibility evidence, not proof that this APK resolves all
four previously observed metadata warnings. Further reports should omit personal names,
addresses, device identifiers and raw household captures.

GitHub previews gather compatibility feedback. Google Play enrollment and a verified
App Bundle distribution pipeline remain separate work.
