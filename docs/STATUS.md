# Current status

**Network Lens 0.3.32 is a development preview for Android 8.0 and newer.** It targets Android API 37 and is distributed through GitHub as a signed APK.

This release recognises devices reporting manufacturer **ARRIS** and model **FibeTV** as **ARRIS FibeTV**, with a TV receiver label and the useful room name beneath it. It preserves original fields and sources and does not infer a specific hardware generation.

Hydrawise and B-hyve product suggestions based on router names have been removed. Local DNS names remain diagnostic information under **Show technical details** and in troubleshooting reports; they do not supply product identity, primary type, presence or grouping. Names are masked in default troubleshooting reports.

[Changes](CHANGES.md) · [Release verification](VERIFICATION-0.3.32.md) · [Recognition rules](reference/DEVICE-IDENTITIES.md)

## What remains unverified

Real /22 timing and affected-device recognition still need physical testing. The scan budget now scales from 55 seconds for a /24 to 100 seconds for a /22, with time reserved for device details. Unfinished checks remain visible; this is not a guarantee of complete discovery.

The reported Google Play Protect classification remains unresolved. The updater handles Android installation blocks more clearly and discards the blocked download; it does not establish Play Protect clearance. Keep Play Protect enabled. See [update troubleshooting](TROUBLESHOOTING.md#google-play-protect-blocks-the-apk).

Synthetic checks do not establish household recognition accuracy. Physical firmware compatibility, multicast delivery, sleeping devices, wireless isolation, low-memory performance and TalkBack need broader testing. MAC and exact model availability depend on device responses and Android restrictions; unsupported details remain unknown.

Google Play enrollment and a verified App Bundle publication path remain separate work. The current release is a direct-download preview.
