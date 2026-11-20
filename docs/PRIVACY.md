# Privacy and permissions

## Your scan stays local

Discovery uses the selected Wi-Fi and eligible scan range. Recognition uses device responses and bundled offline catalogues. Network Lens does not send scan results to a cloud recognition service.

Optional local name lookups query only a configured on-link private DNS server on the selected Wi-Fi, for endpoints that have already responded. The app does not send these requests directly to public DNS resolvers. Router names remain separate from device-reported identity and are masked in the default troubleshooting report. A local DNS server may apply its own forwarding and logging policy.

There are no accounts, analytics, advertisements, background scans or saved device inventory. Scan results, appearance, filters and range choices stay in memory only. A new scan starts fresh; restarting the process clears the current session.

Copying device details is explicit and uses Android's clipboard. Treat copied text and screenshots as private network information, and redact them before sharing.

**Copy recognition report** is a separate, explicit clipboard action. It copies counts, fixed diagnostic labels, the app version/build and Android API version. It excludes addresses, device and personal names, raw model/manufacturer strings, identifiers, credentials and raw responses. The app does not save or upload this report. Android manages the clipboard; you choose where to paste it.

## Detailed troubleshooting reports

**Scan details → Troubleshooting report** previews evidence for all devices, including unnamed ones. Names and addresses are masked by default, with an explicit option to include them for support. Useful model, firmware, service and diagnostic fields remain visible; unknown values, serials and credentials are withheld. Device-supplied text can contain custom information, so review before sharing. This is not guaranteed anonymisation.

**Share full report** gives your chosen app a temporary, read-only text attachment streamed from memory. Network Lens does not save it to disk or upload it itself. The temporary copy expires after ten minutes and is cleared by a new scan, a network change detected by Network Lens, or process exit. If another app is open, Network Lens checks for network changes when you return; the attachment may remain available until then or until it expires. The recipient can retain its own copy. See [report coverage and limits](reference/TROUBLESHOOTING-REPORT.md).

## Internet access for updates

When you choose **Check for updates**, the app contacts public GitHub release endpoints. Downloading an update retrieves the APK from GitHub's release delivery service. This uses the phone's normal internet connection and may use mobile data. Scan results are not included in update requests.

Update APKs use temporary private cache. The app checks package identity, version, digest and the pinned signing certificate before asking Android to install. A GitHub digest checks transfer integrity; the signing certificate is the publisher identity check.

## Android permissions

| Access | Purpose |
| --- | --- |
| Network, Wi-Fi state and multicast | Discover devices on the selected Wi-Fi |
| Local network, where Android enforces it | Allow local discovery traffic |
| Optional coarse/fine location | Display the Wi-Fi name; no coordinates are requested |
| Install packages | Let Android request approval for an update you chose |

There is no camera, microphone, contacts, Bluetooth, storage or background-location permission. Android and device-management rules can restrict discovery or installation even when the app requests access.

This describes the direct-download preview. Google Play enrollment and its distribution requirements remain separate work.

[Technical update policy](reference/APP-UPDATES.md) · [Security reporting](../SECURITY.md) · [Documentation home](README.md)
