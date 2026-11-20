# What is new

## 0.3.32 — FibeTV recognition and clearer identity

- Recognise devices reporting manufacturer `ARRIS` and model `FibeTV` as **ARRIS FibeTV**, with a TV receiver label and the useful room name beneath it. This identifies the receiver family without guessing a hardware generation.
- Remove Hydrawise and B-hyve product suggestions based on router names. Those names remain diagnostic information under technical details and in troubleshooting reports.

## 0.3.31 — Wi-Fi irrigation recognition

- Suggest Hunter Hydrawise and supported Orbit B-hyve families when a responding device’s local network name is corroborated by reverse and forward lookups.
- Label these suggestions **Likely** and keep router names separate from device-reported identity. Stronger product evidence takes precedence.
- Include local-name lookup outcomes in troubleshooting reports, with names masked by default.
- Avoid reporting a local-name check as unfinished when no eligible DNS server is available.
- Give recognised Kidde smart detectors the Lucide smoke-alarm icon.

This uses Wi-Fi only, without Bluetooth, new permissions or cloud recognition. Family suggestions depend on names supplied by the local network; they do not verify an exact model. Physical B-hyve compatibility remains unverified.

## 0.3.30 — Kidde smart detector recognition

- Identify supported Kidde WD-family devices from matching live discovery records, with a useful name and sensor icon.
- Keep friendly room names visible and preserve original reports in technical details.
- Explain when the exact model or smoke/CO capabilities are not available.
- Label the HomeKit service as an advertisement, without implying verified compatibility.
- Reject ambiguous model fields and preserve recognition safeguards across grouped addresses.

No new permissions, network probes, account access or cloud recognition are added.

## 0.3.29 — Clearer recognition and more time for larger networks

- Avoid mistaking supported media-server software and generic service labels for the device itself.
- Recognise more reported MacBook Pro models, TP-Link KP200 outlets, Sony BRAVIA TVs, HP printer families, Synology NAS models and Fosi S3 streamers.
- Reduce duplicate entries when an AirPlay device reports a different interface address, while keeping genuine adapter conflicts separate.
- Mask additional device identifiers in troubleshooting reports, give different readers a fair share of report space, and explain when information could not be retained.
- Give larger supported networks more scan time: up to 70 seconds for a /23 and 100 seconds for a /22. Normal /24 scans retain their 55-second budget, and time is reserved for device details.

### Blocked updates

- Stop and discard the downloaded APK when Android reports a security or policy block, with guidance to keep Play Protect enabled.
- Document the difference between APK integrity checks and Google Play Protect clearance. This does not resolve the reported Play Protect classification.

## 0.3.28 — More consistent recognition

- Recognise supported iPhones and iPads from 179 unambiguous board identifiers, including when another service reports the equivalent product code.
- Resolve compatible Brother printer and Chromecast model reports without losing the original evidence.
- Give supported Brother printers, Amazon Echo Dot and TP-Link KP400 plugs their appropriate device categories.
- Avoid false Apple-conflict warnings for third-party AirPlay devices such as thermostats.
- Treat unavailable optional Cast group information as a finished check, while keeping genuine errors visible.
- Prioritise identity evidence and lookup outcomes in busy-device troubleshooting reports, with explicit counts when bounded records are displaced or omitted.

These changes use information devices already report. They do not add network probes or guarantee identification of devices that withhold their model.

## 0.3.27 — Troubleshooting reports and smoother details

- Share one troubleshooting report covering every retained device and address, including unnamed devices and this phone.
- See reported identity, recognition evidence, services, ports, useful returned fields and lookup history together.
- Preview the report before sharing. Names and addresses are masked by default, with a clear option to include them. Capture limits and withheld fields are explained.
- Share a temporary attachment from memory; no scan history is saved automatically.
- Copy a separate counts-only recognition summary from Scan details.
- Keep device details steady while scrolling and preserve your position when connection information changes.

## 0.3.26 — Broader recognition and clearer device details

- More useful family labels across the bundled integration rules, with explanations when an exact product cannot be identified.
- Public Ubiquiti discovery and passive MikroTik announcements for supported network equipment.
- Scanner information from advertised eSCL services, kept separate from the computer or other host sharing the scanner.
- More discovery opportunities after missed replies, IPv6 SSDP, and fairer limits for advertised IPv6 addresses.
- A smart-home icon for hubs and bridges, and a taller remote-control icon for remotes and recognised Logitech Harmony Hubs.
- Rewritten documentation with short user guides, a technical reference section and archived verification records.

There are no new permissions, saved scan history, background scans or cloud recognition. Device and firmware compatibility still vary; this is a development preview.

Update through **Options → Check for updates** or download the APK from [GitHub Releases](https://github.com/kazands/network-lens/releases/tag/v0.3.32).

[Verification for this release](VERIFICATION-0.3.32.md) · [Earlier releases](https://github.com/kazands/network-lens/releases) · [Documentation](README.md)
