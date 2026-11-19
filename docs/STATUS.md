# Current status

**Network Lens 0.3.28 is a development preview for Android 8.0 and newer.** It targets Android API 37 and is distributed through GitHub as a signed APK.

This release improves recognition when devices report equivalent model identifiers, adds reviewed printer, speaker and plug categories, and keeps important identity evidence in busy-device troubleshooting reports. Optional Cast information no longer creates a warning when a receiver simply does not provide it; genuine failures remain visible.

[Changes](CHANGES.md) · [Release verification](VERIFICATION-0.3.28.md) · [Troubleshooting reports](reference/TROUBLESHOOTING-REPORT.md)

## What remains unverified

Synthetic tests do not establish a household accuracy percentage. Physical firmware compatibility, multicast delivery, sleeping devices, wireless isolation and low-memory performance still need testing across more networks. Physical TalkBack verification is separate from emulator assertions.

MAC and exact model availability depend on device responses and Android restrictions. Some devices expose identity only through authenticated or cloud interfaces. Network Lens does not guess missing information or claim a complete inventory.

Google Play enrollment and a verified App Bundle publication path are not complete. The current release remains a direct-download preview.

For an observed problem, start with [Troubleshooting](TROUBLESHOOTING.md). Older results and implementation investigations are in the [archive](archive/README.md).
