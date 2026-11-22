# Get started

Current scans discover IPv4 devices on the selected Wi-Fi. IPv6-only devices and routed Thread endpoints are outside scope. See [IPv4 scope and limitations](reference/IPV4-SCOPE.md).

Network Lens runs on Android 8.0 or newer. It is currently distributed as a development-preview APK on GitHub.

## Install

1. [Download Network Lens 0.3.39](https://github.com/kazands/network-lens/releases/download/v0.3.39/Network-Lens-0.3.39.apk).
2. Open the downloaded APK. If Android asks, allow your browser or file manager to install it.
3. Follow Android's installation prompt, then open Network Lens.

No GitHub account is needed. Use the APK from the project release page. Source files, checksums and verification notes are available on [the same release](https://github.com/kazands/network-lens/releases/tag/v0.3.39).

If you already use a public Network Lens APK, install the update over it. Do not uninstall first. A locally built development APK may use a different signing key; see [installation troubleshooting](TROUBLESHOOTING.md#android-will-not-install-the-update).

## Scan your Wi-Fi

Connect to the Wi-Fi you want to inspect, then tap **Scan Wi-Fi**. Allow local-network access if Android requests it. Keep Network Lens in the foreground until the scan finishes. You can stop a running scan.

Wi-Fi-name access is optional. Declining location permission can hide the connected network's name; the app does not request your coordinates.

Each scan starts fresh. Wake a missing device and run another scan to give it another opportunity to respond. A new scan does not reuse an old address's identity.

## Read the list

Search for a device by the information it reported. Recognised products show a product name and device icon, with a useful room name underneath. An **Unnamed device** responded but did not supply enough information for a useful name.

Tap a row for more information. The identity card labels the preferred **IPv4** address and shows **MAC** only when that endpoint supplies a suitable hardware address. **Services & ports** follows the identity card. Open **Show technical details** for every grouped address, response timings, reported fields, their sources and lookup outcomes.

Use **Scan details** to investigate discovery methods that could not finish or did not receive usable responses. The scan's finished/stopped status and individual method outcomes describe different things.

Use the category filters to narrow the list to unnamed devices, network equipment, smart-home devices, computers or media. Identification explanations and all endpoint observations remain under **Show technical details**. Search includes MAC addresses and services even though they do not appear in list rows. Press the keyboard's **Search** button to dismiss it and read the results. **Clear search** removes the search term; **All** removes the category filter. IPv6-only devices are outside discovery scope.

The manual Envisalink check has been removed. It could temporarily occupy the alarm interface’s single integration connection. Network Lens no longer offers a dedicated TPI identity check; an address without other identifying reports can remain unnamed.

Results, search filters, appearance and range choices stay in memory. Closing the process clears them; there is no saved inventory or history.

## Update from the app

1. Open **Options → Check for updates**, or **Settings → App updates**.
2. Tap **Download update** if a newer version is available. Keep the app open.
3. Tap **Install update**. If asked, allow installs from Network Lens, return and try again.
4. Approve the Android prompt. Reopen Network Lens and start a fresh scan.

Updates are checked only when requested. The app verifies the APK before asking Android to install it. It does not install silently. Release notes are currently available on GitHub, not inside the update dialog.

[Understanding recognition](RECOGNITION.md) · [Troubleshooting](TROUBLESHOOTING.md) · [Documentation home](README.md)
