# Privacy and permissions

## Your scan stays local

Discovery uses the selected Wi-Fi and eligible scan range. Recognition uses device responses and bundled offline catalogues. Network Lens does not send scan results to a cloud recognition service.

There are no accounts, analytics, advertisements, background scans or saved device inventory. Scan results, appearance, filters and range choices stay in memory only. A new scan starts fresh; restarting the process clears the current session.

Copying device details is explicit and uses Android's clipboard. Treat copied text and screenshots as private network information, and redact them before sharing.

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
