# Network Lens 0.3.38 verification

Prepared on 2026-11-21. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **66**.

## Reviewed behavior

Device rows give product and reported names more room without competing category or service badges. Useful room names, the preferred address and grouped-address count remain visible. Narrow layouts and enlarged text give names the full card width.

Device details show labelled IPv4 and an available endpoint MAC in the identity card, followed by the unchanged Services & ports section. The header never borrows a sibling endpoint's MAC or pairs an advertised other-interface identifier with the displayed IP. Grouped addresses, individual copy actions, response timings, identification evidence, audio observations and MAC sources remain under collapsed technical details.

The compact scan card preserves complete status messages and 48dp actions. Search filters live, keeps the active editor/list state, and hides the keyboard and releases focus when Search is pressed. Query retention, editing, clearing and Back behavior are covered.

A fresh independent Deep review and coordinator verification approved the implementation with no substantive findings. Release preparation changes version metadata and documentation; discovery, recognition, IPv4 scope, current-response rules and RAM-only state are unchanged.

## Build and host checks

All **55 host suites**, Python diagnostics and generated-data/coverage checks passed for this release preparation. Development, public-signed and instrumentation direct builds passed. Packaged permission and security audits passed for both app APKs.

Signing preflight verified the pinned public identity and existing recovery receipt. The isolated signing harness passed **116 assertions** using generated fixture credentials. No signing identity was replaced.

All **107 non-manifest payload entries** match the reviewed implementation candidate. Only the version manifest changed. All **108 non-signature payload entries** match between the development and public APKs.

## Exact versioned Android checks

Guarded isolated emulators verified their identity and installed app/test APK hashes. The exact 0.3.38 development APK passed all **12 selected mode results**, totalling **7,749 assertions**. Counts can overlap and measure synthetic behavior, not physical-device coverage or discovery accuracy.

| Mode | API 26 | API 37 |
| --- | ---: | ---: |
| `compose-device-details` | 778 | 778 |
| `compose-device-rows` | Not run | 2849 |
| `compose-design` | Not run | 274 |
| `compose-search` | 193 | 193 |
| `compose-ui` | 120 | 120 |
| `compose-typography` | 1191 | 1193 |
| `compose-list-anchor` | 30 | 30 |
| **Total** | **2,312** | **5,437** |

The search suite uses field taps, actual key events and the current platform IME action in both themes at 375dp and 320dp / 200% text. The tests verify keyboard visibility and focus, submitted query/result retention, editing, clearing and private keyboard flags. They do not exercise every OEM soft-keyboard composition behavior.

## Public signed update

The exact public-signed APK passed **23 update/launch checks** from public 0.3.37 on the isolated API 37 emulator. Both certificates and installed bytes were verified. The update retained the app UID and all three established local-network/location runtime grants. Launch waited for a user-started scan. This uses `adb install -r`, not the live GitHub in-app installation journey.

## Artifact identity

- Public APK SHA-256: `add61e3c02808d23e1b59b214e792b9314e21a51fa5e374e8b1b694ca73b9b1a`.
- Development APK SHA-256: `e3ca204786f2abc5a6dd34c8f54926335eefaf3c62012bcc01cba3578894e85c`.
- Instrumentation SHA-256: `7ebce3d780ed527ce01b9ddd237588aa7db82958367a50267f9c1ba044f39b15`.
- Reviewed implementation candidate SHA-256: `e04c925398716287ec5f9e799950e453d7abd71d645fc15386e97f06fe734575`.
- Public certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

## Limits

Physical-phone and OEM-keyboard behavior, TalkBack, ARM64 performance, low-memory behavior, optional Wi-Fi-name permission flow and live GitHub in-app installation remain unverified. No physical device was operated. These interface changes do not claim improved discovery or recognition. IPv6-only devices and routed Thread endpoints remain outside discovery scope. The earlier Play Protect classification remains unresolved; signing and emulator checks do not establish Google clearance.

The matching source package identifies the merged commit/tree and includes required public inputs, per-file hashes and original notices. Credentials, local caches and household evidence are excluded. Assets are published with SHA256SUMS.

[Build instructions](../BUILDING.md) · [Previous release verification](VERIFICATION-0.3.37.md)
