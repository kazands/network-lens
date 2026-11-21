# Current status

**Network Lens 0.3.35 is a development preview for Android 8.0 and newer.** It targets Android API 37 and is distributed through GitHub as a signed APK.

## This release

Unresolved devices that responded during the current scan can now receive bounded public HTTP, certificate-observation, SSH and direct LLMNR hostname checks. Selected clues appear in technical details and troubleshooting reports; public software-service evidence stays separate from hardware identity. Queue wait and request timing help explain why a lookup did or did not return information. See [public identity readers](reference/PUBLIC-IDENTITY.md).

Troubleshooting reports mask public labels by default. The explicit names, local-addresses and public-labels option can include them for investigation. Cookies, authentication nonces, raw certificates, keys and unreviewed response bodies are not retained.

## App behavior

The Compose interface keeps one searchable device list, adaptive details, category filters and source-labelled observations. Results, appearance and navigation stay in RAM. Scans start only when requested; current responses are required for presence. Router DNS names cannot determine a product, manufacturer, primary type or grouping.

No dedicated Envisalink TPI probe is used, because even a short connection could compete with an alarm integration. Devices without supported identifying reports can remain unnamed.

[Changes](CHANGES.md) · [Release verification](VERIFICATION-0.3.35.md) · [Recognition rules](reference/DEVICE-IDENTITIES.md) · [Implementation verification history](PUBLIC-IDENTITY-VERIFICATION.md)

## What remains unverified

Physical recognition gains, firmware compatibility, ARM64 performance, low-memory behavior, TalkBack, the optional Wi-Fi-name permission journey, receiving-app compatibility and OEM capture behavior need broader testing. Synthetic emulator checks do not establish household accuracy or a complete inventory.

The unshrunk APK remains about 15 MB with four native ABIs. The supported direct build verifies pinned runtime dependencies, original notices, packaged permissions and components. The Gradle IDE APK path remains unverified. Credential-based SNMP and production IPv6 multicast probing remain follow-up work.

The reported Google Play Protect classification remains unresolved. Successful signing and emulator installation do not establish Google clearance.
