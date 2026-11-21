# Network Lens 0.3.36 verification

Prepared on 2026-11-20. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **64**.

## Reviewed behavior

The Compose interface uses explicit light/dark Material colour roles, shared
typography and shape tokens, and muted icon tiles following the existing physical
category filters. The scan card separates the network name and scope, uses an
adaptive layout at enlarged text/narrow widths, and replaces the spinner and
linear bar with one decorative radar. Counts update directly. Separate group and
address counts remain in Scan details. Source-labelled observations, the system
font, RAM-only state and navigation/scroll restoration are preserved.

The radar observes Android's animation setting while open and removes its
infinite transition off-screen, while paused, at scan end or with motion disabled.
The closing ring requires an observed scan-to-finished transition and a complete,
non-stopped report with results. Address percentages still describe only the first
pass; identification does not show an invented overall 100%.

A fresh independent Deep kaz-code-review and coordinator verification covered
all 14 implementation/test/documentation files and approved the change with no
substantive findings. Independent contrast calculations passed. The pre-release
implementation candidate, labelled 0.3.35, passed 4,972 Android UI assertions on
API 26/37. That earlier candidate is distinguished from the numbered release
results below. Release preparation changes version metadata and documentation.

## Build and host checks

Development, public-signed and instrumentation direct builds passed. Permission
and security audits passed for both application APKs, including the existing
permission/component inventory, pinned runtime/native hashes and alignment
checks. Signing preflight verified the existing public identity and recovery
receipt. No replacement key was introduced.

All **55 host suites** and **29 Python public-trial tests** passed during release
preparation, along with the generated-data and recognition-policy checks.
Repository guardrails, documentation links and diff checks are recorded with the
final release validation. Guardrails are not a guarantee that private data is absent.

All **107 non-manifest application payload entries** match the reviewed
implementation candidate. The only payload difference is `AndroidManifest.xml`,
for 0.3.36/build 64. All **108 non-signature payload entries** match between the
development and public APKs.

## Exact versioned Android checks

Both guarded isolated emulators rejected physical targets and verified the
installed application and instrumentation hashes. These results use the exact
versioned development APK and instrumentation listed below.

| Mode | API 26 | API 37 |
| --- | ---: | ---: |
| `compose-design` | 170 | 178 |
| `compose-ui` | 146 | 159 |
| `compose-typography` | 1432 | 1451 |
| `compose-list-anchor` | 30 | 30 |
| `compose-rtl` | 12 | 10 |
| `compose-privacy` | 109 | 203 |
| `compose-reports` | 157 | 157 |
| `compose-updates` | 364 | 364 |
| **Total** | **2420** | **2552** |

Total: **4,972 successful Android assertions across 16 mode results**.
Counts overlap and can vary with rendered layouts; they are not distinct
products or accuracy measurements. Both themes, enlarged text, narrow/wide
layouts, scan motion and live reduced-motion changes, navigation/scroll anchors,
RTL, report privacy and updater states were exercised.

The initial API 26 design-mode attempt stopped at its Wi-Fi prerequisite before
the scan simulation. It passed after a fresh emulator reached Wi-Fi readiness.
An initial parallel API 37 emulator failed to boot before any app test; a
sequential restart booted successfully and passed all eight modes. These setup
failures are retained in local evidence and excluded from the successful counts.

## Public signed update

The exact public APK passed **23 signed update and launch checks** from
public 0.3.35 on the isolated API 37 emulator. Both certificates and installed
APK bytes were verified. Updating retained the application UID and all three
established local-network/location runtime grants. The app launched and waited
for a user-started scan. These checks use `adb install -r`, not the live GitHub
in-app download/install path.

## Artifact identity

- Public APK SHA-256: `3a147d63b6352a741d54dbde40b48343e6bc619eacd65ef1fda05176194dcf16`.
- Development APK SHA-256: `7f8e10d7c6b7cc7830c680d50e6c8a226ade281ec53ba67e0b65084c1184c0eb`.
- Instrumentation SHA-256: `2b4fcab9fd0fe8635a5be0285622ff2b5befd9a834bac0f806ff29487531d820`.
- Reviewed implementation candidate SHA-256: `19fd55b5823cd68bd0e976a9473553d07b5432fa4a9e3fa887ab2bcaea4e826a`.
- Public certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

## Limits and manual verification

Physical phone/ARM64 execution, low-memory performance, gestures and TalkBack
remain manual checks. No household scan or physical phone operation was
performed. This interface change does not establish improved recognition, a
complete device inventory or a battery benchmark. Discovery and recognition
rules, current-response presence, selected-Wi-Fi binding and report privacy are
unchanged. No dependencies, permissions, saved inventory, cloud recognition,
background scanning or device-control commands were added.

Live GitHub download and user-approved in-app installation, receiving-app/OEM
capture integration and the Gradle IDE APK path remain outside these checks.
The prior Play Protect classification remains unresolved; signing and emulator
installation do not establish Google clearance. Keep Play Protect enabled.

The matching source package identifies the merged commit/tree and includes
per-file hashes, required public regeneration inputs and original notices.
Private signing material, caches and household evidence are excluded. Assets
are published with `SHA256SUMS`.

See the [design system](../reference/DESIGN-SYSTEM.md), [build instructions](../BUILDING.md)
and [previous release verification](VERIFICATION-0.3.35.md).
