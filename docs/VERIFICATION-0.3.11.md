# Network Lens 0.3.11 verification

Verified locally on 2026-10-30 for the 0.3.11 development pre-release. This update improves typography and IPv6 presentation. Discovery behavior and its known limitations are unchanged.

## Identity and artifacts

- Application ID: `io.github.kazands.networklens`.
- Version: 0.3.11 / code 39; minimum API 26, target API 37.
- Development APK SHA-256: `7f5590dcdfeac7b3f12691b728353341c81233e6d9d2bc5917d2d8dd49745081`.
- Public app-signing APK SHA-256: `e71d726f6e5180177619eef33934c715b4b4c62728a8156f13fad40d05964810`.
- Retained public signing certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

The signed APK updates the production-signed 0.3.10 preview in place. Both direct builds passed signature verification, permission allowlisting and packaged security checks. Their 30 application ZIP entries match after excluding signature metadata. No signing key was replaced or regenerated.

## Typography and IPv6 changes

The app retains native sans-serif and monospace fonts. Main screens and dialogs share scalable sizes, weights and line spacing. Supporting list text and detail labels are larger; detail labels use sentence case, and missing-value messages use ordinary interface text. The search hint is now “Search devices.”

IPv6 remains below IPv4. Address text follows Android's font-size setting, uses left-to-right numeric layout and wraps without a fixed line limit, ellipsis or automatic shrinking. Hexadecimal groups stay together when wrapping. Mixed IPv6/IPv4 tails and interface scopes can wrap within their available width. Display spans do not insert characters into the underlying address; selection, search and copied details retain the complete original value.

## Automated checks

- 19 shared-logic host suites passed: 13,735 assertions plus malformed-input loops. Shared discovery logic is unchanged by this presentation update.
- 116 release-signing integration assertions passed against the versioned 0.3.11 development APK using isolated synthetic keys.
- Both direct builds and the Android instrumentation build passed.

The typography implementation was tested before the final version bump using these emulator runs:

| Android API | Modes | Checks | Result |
| --- | --- | --- | --- |
| 26 | 6 | 874 | PASS |
| 36 | 6 | 903 | PASS |
| 37 | 6 | 876 | PASS |

Each platform passed 613 dedicated typography checks. Synthetic cases cover long global IPv6, scoped link-local IPv6, compressed IPv6, mixed IPv6/IPv4, dual-stack grouping, exact copy, search and IPv4/IPv6 order. Native layouts are measured at 320dp and 411dp widths, normal and 200% font settings, and both appearances. Checks inspect every line for truncation, overflow and splits inside hexadecimal groups. Related modes cover details, Settings, dialogs, navigation, branding or existing address-layout regressions.

The final 0.3.11 APK's application payload is byte-identical to that tested typography build except for `AndroidManifest.xml`, which contains the new version. Application DEX and resources are unchanged. The versioned 0.3.11 development APK additionally passed 728 typography and Settings checks on API 36. The exact public-signed APK passed 10 Android 17 emulator checks covering installation over the published 0.3.10 APK, installed-byte verification, version, nonempty runtime-grant retention, branding and no automatic launch scan.

## Physical Pixel 8

The explicitly authorized Pixel 8 runs Android 17. Its installed 0.3.10 APK matched the published public artifact. Version 0.3.11 installed as an update without uninstalling; installed bytes match the public-signed candidate, and existing local-network and optional location grants were retained.

At the phone's unchanged default text setting, synthetic sample screens were inspected in light and dark appearance. The list, search, sentence-case address labels, unknown values, Settings and toolbar Back were checked. The original in-app appearance was restored. Captured phone images contain synthetic sample devices only.

The first sample-mode request did not expose the expected sample screen. A later foreground check stopped when Network Lens was no longer in the foreground; the user reopened it. A fresh same-app activity then displayed the sample and the UI checks passed. These setup attempts did not require an application change and do not establish an application crash or its cause.

One uninterrupted physical Wi-Fi scan reached a terminal **Partial** result: 41 methods finished, one failed and one was unused. The failed source was HomeKit mDNS (`_hap._tcp`): native registration supplied no address update. Every failed source and reason was read before continuing. A fresh IPv6 address with an interface scope was visible in the physical list with nonempty layout bounds. Full line-by-line wrapping and exact-copy assertions were performed in emulators; the physical check did not change the system text-size setting.

No household device names, addresses, SSIDs, serials or personal screenshots are included in this public record. The phone retains the exact signed release candidate. The physical scan remains Partial; the UI checks do not resolve the HomeKit discovery issue.

## Limits and release package

Physical 200% text, formal TalkBack/accessibility certification, broader phone/router coverage and real Thread-mesh completeness remain unverified. The existing intermittent HomeKit (`_hap._tcp`) and HTTP (`_http._tcp`) native address-resolution failures remain open. A Partial scan remains Partial; these typography changes do not fix discovery.

The release package contains the exact signed APK, corresponding committed source, `SHA256SUMS` and these verification notes. The source archive includes upstream source/notices and reproducible public IEEE inputs; its `RELEASE-SOURCE.json` records the exact commit and per-file hashes. Private credentials, local tools, household evidence and personal screenshots are excluded. Older release assets and tags are retained.
