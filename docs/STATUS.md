# Current status

**Network Lens 0.3.36 is a development preview for Android 8.0 and newer.** It targets Android API 37 and is distributed through GitHub as a signed APK.

## This release

The scan card separates the network name and range, adapts to narrow screens and enlarged text, and uses one radar indicator. Address-check percentages describe only the first pass; identification continues afterwards. Animation responds to Android's setting while open and stops when the card is off-screen or the app is paused. A closing ring is reserved for a newly completed successful scan with results.

Muted category colours make device icons easier to scan. Menus, dialogs and scan details have clearer surfaces in both themes. The list uses plain device counts, with separate group and address counts retained in Scan details. See the [design system](reference/DESIGN-SYSTEM.md). Discovery and recognition rules are unchanged.

## App behavior

The Compose interface keeps one searchable device list, adaptive details, category filters and source-labelled observations. Results, appearance and navigation stay in RAM. Scans start only when requested; current responses are required for presence. Router DNS names cannot determine a product, manufacturer, primary type or grouping.

Bounded public identity readers retain selected local clues separately from hardware identity. Troubleshooting reports mask public labels by default; their explicit names-and-labels option can include them for investigation. Credentials, cookies, raw certificates and unreviewed bodies are excluded.

No dedicated Envisalink TPI probe is used, because even a short connection could compete with an alarm integration. Devices without supported identifying reports can remain unnamed.

[Changes](CHANGES.md) · [Release verification](VERIFICATION-0.3.36.md) · [Recognition rules](reference/DEVICE-IDENTITIES.md) · [Implementation verification history](PUBLIC-IDENTITY-VERIFICATION.md)

## What remains unverified

Physical recognition gains, firmware compatibility, ARM64 performance, low-memory behavior, TalkBack, the optional Wi-Fi-name permission journey, receiving-app compatibility and OEM capture behavior need broader testing. Synthetic emulator checks do not establish household accuracy or a complete inventory.

The unshrunk APK remains about 15 MB with four native ABIs. The supported direct build verifies pinned runtime dependencies, original notices, packaged permissions and components. The Gradle IDE APK path remains unverified. Credential-based SNMP and production IPv6 multicast probing remain follow-up work.

The reported Google Play Protect classification remains unresolved. Successful signing and emulator installation do not establish Google clearance.
