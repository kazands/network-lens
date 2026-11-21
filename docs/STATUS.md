# Current status

**Network Lens 0.3.37 is a development preview for Android 8.0 and newer.** It targets Android API 37 and is distributed through GitHub as a signed APK.

## This release

Scans now focus on responding IPv4 devices. IPv6-only devices and routed Thread endpoints are outside the inventory. Wi-Fi without an IPv4 address shows an explicit requirement and keeps Scan disabled. Native Android service updates retain a bounded opportunity for a later IPv4 address; cached advertisements still cannot prove presence or identity.

Read-only metadata lookups classify typed connection refusals and routing failures before deciding whether to retry. Transient failures retain one bounded fresh-connection retry. These changes do not guarantee that a silent device responds or reports a model.

## App behavior

The Compose interface keeps one searchable device list, adaptive details, category filters and source-labelled observations. Results, appearance and navigation stay in RAM. Scans start only when requested; current responses are required for presence. Router DNS names cannot determine a product, manufacturer, primary type or grouping.

Bounded public identity readers retain selected local clues separately from hardware identity. Troubleshooting reports mask public labels by default; their explicit names-and-labels option can include them for investigation. Credentials, cookies, raw certificates and unreviewed bodies are excluded.

No dedicated Envisalink TPI probe is used, because even a short connection could compete with an alarm integration. Devices without supported identifying reports can remain unnamed.

[Changes](CHANGES.md) · [Release verification](VERIFICATION-0.3.37.md) · [Recognition rules](reference/DEVICE-IDENTITIES.md) · [Implementation verification history](PUBLIC-IDENTITY-VERIFICATION.md)

## What remains unverified

Physical recognition gains, firmware compatibility, ARM64 performance, low-memory behavior, TalkBack, the optional Wi-Fi-name permission journey, receiving-app compatibility and OEM capture behavior need broader testing. Synthetic emulator checks do not establish household accuracy or a complete inventory.

The unshrunk APK remains about 15 MB with four native ABIs. The supported direct build verifies pinned runtime dependencies, original notices, packaged permissions and components. The Gradle IDE APK path remains unverified. Credential-based SNMP remains outside the current scope. LAN discovery is now IPv4-only; see [IPv4 scope](reference/IPV4-SCOPE.md) for the synthetic comparison and coverage tradeoff.

The reported Google Play Protect classification remains unresolved. Successful signing and emulator installation do not establish Google clearance.
