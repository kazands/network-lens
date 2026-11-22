# Current status

**Network Lens 0.3.40 is a development preview for Android 8.0 and newer.** It targets Android API 37 and is distributed through GitHub as a signed APK.

## This release

Additional TCP reachability checks cover ports 8012, 10101 and 11095 during unanswered-address recovery and unidentified-device enrichment. These ports are not product fingerprints. Normal scan deadlines, current-response requirements and IPv4 scope still apply. Apple response diagnostics now distinguish fixed transport failure categories and timings.

Physical confirmation of the tester's Nest cameras and the previously reported missing Apple devices remains outstanding. No dedicated Nest/Weave identity reader is included.

The clearer device rows, compact scan card and keyboard search submission from 0.3.38 remain available. Scans admit responding IPv4 devices; IPv6-only devices and routed Thread endpoints remain outside the inventory. Cached advertisements cannot prove presence or identity.

## App behavior

The Compose interface keeps one searchable device list, adaptive details, category filters and source-labelled observations. Results, appearance and navigation stay in RAM. Scans start only when requested; current responses are required for presence. Router DNS names cannot determine a product, manufacturer, primary type or grouping.

Bounded public identity readers retain selected local clues separately from hardware identity. Troubleshooting reports mask public labels by default; their explicit names-and-labels option can include them for investigation. Credentials, cookies, raw certificates and unreviewed bodies are excluded.

No dedicated Envisalink TPI probe is used, because even a short connection could compete with an alarm integration. Devices without supported identifying reports can remain unnamed.

[Changes](CHANGES.md) · [Release verification](VERIFICATION-0.3.40.md) · [Recognition rules](reference/DEVICE-IDENTITIES.md) · [Implementation verification history](PUBLIC-IDENTITY-VERIFICATION.md)

## What remains unverified

Physical recognition gains, firmware compatibility, ARM64 performance, low-memory behavior, TalkBack, the optional Wi-Fi-name permission journey, receiving-app compatibility and OEM capture behavior need broader testing. Synthetic emulator checks do not establish household accuracy or a complete inventory.

The unshrunk APK remains about 15 MB with four native ABIs. The supported direct build verifies pinned runtime dependencies, original notices, packaged permissions and components. The Gradle IDE APK path remains unverified. Credential-based SNMP remains outside the current scope. LAN discovery is now IPv4-only; see [IPv4 scope](reference/IPV4-SCOPE.md) for the synthetic comparison and coverage tradeoff.

The reported Google Play Protect classification remains unresolved. Successful signing and emulator installation do not establish Google clearance.
