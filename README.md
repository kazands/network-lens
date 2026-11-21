<p align="center">
  <img src="docs/images/network-lens-logo.png" alt="Network Lens" width="128" height="128">
</p>

# Network Lens

See what is on your Wi-Fi. Find responding devices, understand what they are, and inspect the details they share.

Network Lens is a free, open-source Android app for home users and smart-home enthusiasts. It keeps everyday scanning simple while making addresses, services and discovery evidence available when you need them.

[![Download Network Lens 0.3.36 APK](docs/images/download-apk.svg)](https://github.com/kazands/network-lens/releases/download/v0.3.36/Network-Lens-0.3.36.apk)

**Android 8.0 or newer · Development preview · No account required**

Already installed? Open **Options → Check for updates**. Android asks you to approve installation. [Release notes and checksums](https://github.com/kazands/network-lens/releases/tag/v0.3.36)

## Find your devices

1. Connect your phone to Wi-Fi and open Network Lens.
2. Tap **Scan Wi-Fi** and keep the app open while it scans.
3. Search or filter the results, then tap a device to see its identity, addresses and available services.

Recognised products receive useful names and category-coloured icons. Room names stay visible. The scan card brings the network, action and progress together and adapts to enlarged text. Compact rows show the preferred address; device details show every grouped address, with IPv6 below IPv4. **Show technical details** reveals the original reports and their sources, including selected public web, certificate and SSH clues when available.

## Local by design

Recognition runs on your phone using local responses and bundled catalogues. There is no cloud recognition, account, advertising or analytics. Scans are user-started; results and settings stay in memory, with no saved inventory or scan history. Checking for an app update contacts GitHub only when you ask.

Local DNS can supply a separately labelled router name for technical details and troubleshooting reports. Router names can be stale and are never used for product recognition, list titles, manufacturer/model fields or address grouping.

## Know what the result means

A device must respond during the current scan to appear as present. Some devices share a precise model; others share only a name or service. Network Lens leaves unsupported details unknown. A thermostat offering audio services remains a thermostat when its identity is known.

Sleeping devices, network isolation and Android restrictions can limit what a scan finds. **Scan finished** means the scan ended normally; **Scan details** explains individual discovery outcomes. It is not a guarantee that every physical device was found.

## Help and documentation

- [Get started](docs/GETTING-STARTED.md) — install, scan and update.
- [Understanding recognition](docs/RECOGNITION.md) — supported device families and what can be identified.
- [Troubleshooting](docs/TROUBLESHOOTING.md) — missing devices, unnamed results and update problems.
- [Privacy and permissions](docs/PRIVACY.md) — what stays on your phone and why access is requested.
- [Developer guide](docs/BUILDING.md) — build and test the app.
- [All documentation](docs/README.md) — technical references and release verification.

## Troubleshoot with one report

Open **Scan details → Troubleshooting report** to preview and share the retained evidence for every device, including unnamed results. Names, addresses and public service labels are masked by default. You can explicitly include them for troubleshooting. Review the preview before sharing. [What the report includes](docs/reference/TROUBLESHOOTING-REPORT.md).

## Contribute

Read [Contributing](CONTRIBUTING.md) before proposing a change. Recognition reports are welcome: include your app version, the expected device model and redacted technical observations. Remove household names, addresses and identifiers before posting.

Original code, documentation and artwork are **GPL-3.0-only**. Third-party materials retain their own terms; see [LICENSE](LICENSE), [NOTICE](NOTICE) and [third-party notices](android/THIRD_PARTY_NOTICES.md). Network Lens is independent and does not include Fing or Nmap code or recognition databases.
