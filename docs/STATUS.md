# Current status

**Network Lens 0.3.34 is a development preview for Android 8.0 and newer.** It targets Android API 37 and is distributed through GitHub as a signed APK.

## This release

The Compose interface brings scan controls and status together, adds category filters and larger device cards, and gives device details an adaptive layout with every grouped address and source-labelled observation. Wider screens can show the list and details together. Search, navigation and appearance stay in memory.

Update progress and Cancel remain visible across Wi-Fi changes. Automatic content capture and autofill are excluded from every Compose window, while screenshots, accessibility and explicit copy/share remain available. Report streams have bounded writes and cancellation on Android 8 and newer.

The manual Envisalink action and dedicated TPI reader have been removed because even a short connection could compete with an alarm integration. Devices without other identifying reports can remain unnamed. Discovery and recognition otherwise use the existing backend.

[Changes](CHANGES.md) · [Release verification](VERIFICATION-0.3.34.md) · [Recognition rules](reference/DEVICE-IDENTITIES.md) · [Implementation verification history](COMPOSE-VERIFICATION.md)

## What remains unverified

Physical-device scrolling, ARM64 execution, gesture navigation, TalkBack, the optional Wi-Fi-name permission journey, receiving-app compatibility, OEM content capture and low-memory performance still need broader testing. Synthetic emulator checks do not establish household recognition accuracy.

The unshrunk APK is about 15 MB, compared with about 4 MB before the Compose migration. The direct build verifies the locked runtime dependencies, original notices, packaged permissions, components and native libraries. The Gradle IDE APK path remains unverified.

Real /22 timing and affected-device recognition still need physical testing. Scan time scales from 55 seconds for a /24 to 100 seconds for a /22, with time reserved for device details. Unfinished checks remain visible; a finished attempt is not a guarantee of complete discovery.

The reported Google Play Protect classification remains unresolved. Emulator installation does not establish Google clearance. Keep Play Protect enabled; see [update troubleshooting](TROUBLESHOOTING.md#google-play-protect-blocks-the-apk).

Google Play enrollment and a verified App Bundle publication path remain separate work. This release is a direct-download preview, with no new permissions, saved inventory, background monitoring or cloud recognition.
