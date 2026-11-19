# Network Lens 0.3.17 verification

Release prepared on 2026-11-16. Android development preview for physical testing.

## Identity and scope

- Application ID: `io.github.kazands.networklens`.
- Version 0.3.17 / code 45; minimum API 26, target API 37.
- Public signing identity remains pinned in `android/release-signing.json`.
- Explicit Options/Settings update checks include numbered public preview releases.
- Bounded HTTPS downloads check size, SHA-256, package, advancing version/code,
  minimum Android version and the pinned signer before Android installation approval.
- Foreground cancellation, private temporary cache and a non-exported installer
  callback; no automatic checks, background downloads or scan-data uploads.
- New `REQUEST_INSTALL_PACKAGES` permission enables user-requested installation.
  Scan data/preferences stay in memory; no shared-storage permission is added.

## Implementation checks

- All 32 host suites passed, including 67 updater policy/transport checks.
- Independent Deep Kaz review of the complete implementation: Approve, no
  substantive findings. The reviewer independently reran all host suites and build.
- Before versioning, API 26 and API 37 each passed 128 updater checks and 115 menu
  checks. API 37 navigation/session/scan-efficiency modes added 95 checks.
- Tests cover both themes, normal/200% text, cancellation, queued dialog replacement,
  wrong installer callbacks, invalid APKs, signing/version rejection, temporary-file
  cleanup and actual Android pending-user-approval callbacks.
- A live API 37 emulator check contacted the real public feed and displayed the
  installed 0.3.16 development build as up to date. This did not install an APK.

## Release checks

- Versioned development/public builds and packaged permission/security audits passed.
- Development APK SHA-256: `f14b94b674ffdca2f4c9c38f968ebd60d2a61baff40d187f00035ee2540a351e`.
- Public APK SHA-256: `b7bdf4f21b3c80319052bbad1ceaaa28700a577e15f5489ff30e0bf6a941a60a`.
- Public certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
- All 43 application payload entries match between development/public APKs,
  excluding signing metadata.
- All 116 release-signing assertions passed using isolated generated credentials.

- Exact versioned development APK and rebuilt instrumentation passed **338 API 37
  checks**: updater 128, options 115, navigation 31, session-only 39, cold session 3,
  scan-efficiency 22. Installed application and test APK hashes were verified.

- The exact public APK passed **23 update/launch checks** on API 37, updating the
  verified public 0.3.16 APK in place, retaining its UID and all three existing
  runtime permission grants, matching installed bytes/version/code, and opening
  ready for a user-started scan. No scan was started by the upgrade test.
- Focused independent review of release versioning, documentation, APK metadata,
  hashes and development/public payload equality: Approve, no substantive findings.

## Published-download verification and limits

A real in-app download needs the new release to be public. Its post-publication
result is recorded in the GitHub release notes and a supplemental verification
asset. The test uses an older-version private, public-signed build that contains
the updater; the original public 0.3.16 does not contain an update button. This
fixture is never distributed as a release or installed on the user's phone.

The released APK, exact committed source with public regeneration inputs,
`SHA256SUMS` and verification notes are distributed together. Private signing
material and household captures are excluded. No physical discovery results,
physical TalkBack coverage or all-OEM installer compatibility are claimed.
Device-management restrictions can block installation. This update does not
change recognition accuracy or resolve the existing discovery limits in
[STATUS.md](../STATUS.md). Google Play distribution remains separate work.
