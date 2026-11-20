# Current status

**Network Lens 0.3.31 is a development preview for Android 8.0 and newer.** It targets Android API 37 and is distributed through GitHub as a signed APK.

This release adds tentative Hunter Hydrawise and Orbit B-hyve family suggestions from corroborated local network names. It keeps those names separate from device-reported identity, masks them in default troubleshooting reports and uses a smoke-alarm icon for recognised Kidde detectors. No Bluetooth, new permissions or cloud recognition are added.

[Changes](CHANGES.md) · [Release verification](VERIFICATION-0.3.31.md) · [Irrigation matching limits](reference/IRRIGATION.md)

Hydrawise and B-hyve suggestions depend on an eligible local DNS server and matching names. They do not verify an exact model or manufacturer. Physical B-hyve compatibility still needs testing.

## What remains unverified

Real /22 timing and affected-device recognition still need physical testing. The scan budget now scales from 55 seconds for a /24 to 100 seconds for a /22, with time reserved for device details. Unfinished checks remain visible; this is not a guarantee of complete discovery.

The reported Google Play Protect classification remains unresolved. The updater handles Android installation blocks more clearly and discards the blocked download; it does not establish Play Protect clearance. Keep Play Protect enabled. See [update troubleshooting](TROUBLESHOOTING.md#google-play-protect-blocks-the-apk).

Synthetic checks do not establish household recognition accuracy. Physical firmware compatibility, multicast delivery, sleeping devices, wireless isolation, low-memory performance and TalkBack need broader testing. MAC and exact model availability depend on device responses and Android restrictions; unsupported details remain unknown.

Google Play enrollment and a verified App Bundle publication path remain separate work. The current release is a direct-download preview.
