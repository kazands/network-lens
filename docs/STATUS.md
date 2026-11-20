# Current status

**Network Lens 0.3.33 is a development preview for Android 8.0 and newer.** It targets Android API 37 and is distributed through GitHub as a signed APK.

This release makes Envisalink alarm-interface checks explicit. Normal scans no longer probe the alarm integration service. **Show technical details → Check for Envisalink** explains the possible integration conflict and starts a fresh scan with one check requested for that address. The device must respond again; the check also works when a large network uses service discovery without an address sweep.

Troubleshooting reports mask additional IPv6 addresses embedded in retained text and reuse indexed name matching to reduce preparation work. Device-detail and troubleshooting-page copies suppress sensitive clipboard previews on supported Android versions. SSDP gives each sender separate processing limits while retaining overall time, packet and record bounds. Reached limits remain visible in scan details.

[Changes](CHANGES.md) · [Release verification](VERIFICATION-0.3.33.md) · [Recognition rules](reference/DEVICE-IDENTITIES.md)

## What remains unverified

Physical alarm-integration compatibility and report performance on phones still require testing. Clipboard preview protection does not stop a receiving app from retaining pasted information. These changes do not improve recognition for devices that withhold their identity.

Real /22 timing and affected-device recognition still need physical testing. The scan budget now scales from 55 seconds for a /24 to 100 seconds for a /22, with time reserved for device details. Unfinished checks remain visible; this is not a guarantee of complete discovery.

The reported Google Play Protect classification remains unresolved. The updater handles Android installation blocks more clearly and discards the blocked download; it does not establish Play Protect clearance. Keep Play Protect enabled. See [update troubleshooting](TROUBLESHOOTING.md#google-play-protect-blocks-the-apk).

Synthetic checks do not establish household recognition accuracy. Physical firmware compatibility, multicast delivery, sleeping devices, wireless isolation, low-memory performance and TalkBack need broader testing. MAC and exact model availability depend on device responses and Android restrictions; unsupported details remain unknown.

Google Play enrollment and a verified App Bundle publication path remain separate work. The current release is a direct-download preview.
