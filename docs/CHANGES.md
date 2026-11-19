# What is new

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

Update through **Options → Check for updates** or download the APK from [GitHub Releases](https://github.com/kazands/network-lens/releases/tag/v0.3.28).

[Verification for this release](VERIFICATION-0.3.28.md) · [Earlier releases](https://github.com/kazands/network-lens/releases) · [Documentation](README.md)
