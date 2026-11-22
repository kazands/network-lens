# What is new

## 0.3.39 — More reliable VSSL identification

- Identify supported VSSL amplifiers when their Google Cast model arrives without AirPlay manufacturer details.
- Confirm the manufacturer, hardware model and zone through the amplifier’s own read-only status response. Missing or conflicting details remain unknown.

Scans still require current responses on the selected Wi-Fi and keep results in memory only.

## 0.3.38 — Clearer devices and easier search

- Give device names more room by moving category and service badges out of the list rows. Keep useful room names and addresses visible.
- Simplify device details: show labelled IPv4 and an available endpoint MAC in the identity card, followed by the existing **Services & ports** section. Keep grouped addresses, response timings and identification evidence under **Show technical details**.
- Shrink the scan card so the device list starts higher, while preserving full status messages and enlarged-text layouts.
- Dismiss the keyboard when Search is pressed, preserve the query and keep the active editor stable while typing. Keep editing, clearing and returning from a result available.

Discovery and recognition rules are unchanged. Scans still admit responding IPv4 devices on the selected Wi-Fi, with results held in memory only.

## 0.3.37 — Focused IPv4 discovery

- Focus scans on responding IPv4 devices on the selected Wi-Fi.
- Show an explicit IPv4 requirement and disable Scan on Wi-Fi without an IPv4 address.
- Keep normal HTTPS updates and IPv6 address masking in returned text. IPv6-only devices and identity clues available only through IPv6 are outside the inventory; see [scope and validation](reference/IPV4-SCOPE.md).
- Allow a later IPv4 address to arrive after an initial IPv6-only Android service update.
- Avoid retrying typed connection refusals and routing failures during read-only metadata lookups; transient errors retain bounded retries.

## 0.3.36 — Calmer scanning and clearer device categories

- Use consistent light/dark surface colours for cards, fields, menus and dialogs.
- Add muted category colours to device icons while keeping the existing physical-device classification.
- Replace the scan spinner and progress bar with one radar indicator. Address-check percentages and the identification phase remain explicitly labelled.
- Adapt the scan card for narrow screens and enlarged text. Counts update immediately; device grouping and address counts remain available in Scan details.
- Keep the radar static with Android animations disabled, stop it off-screen, and reserve the closing ring for a newly completed successful scan.

These are interface changes. Discovery and recognition behaviour are unchanged.

## 0.3.35 — More clues for unnamed devices

- Check public web information, presented certificate names and SSH greetings when a responding device has no supported product or specific model. Selected clues appear under **Show technical details**.
- Recognise corroborated Home Assistant, Pi-hole, Tasmota and OctoPrint web services. Software labels stay separate from the identity of the hardware hosting them.
- Try a direct local hostname lookup for unresolved responders without a hostname. Router DNS names remain diagnostic information only.
- Show queue wait, remaining time at request start and request duration for supported identity readers.
- Include public-reader outcomes and selected clues in device-detail copies and troubleshooting reports. Clues are masked by default; the names-and-public-labels option explicitly includes them for troubleshooting.
- Reuse equipment root-page requests and keep work, parsing, cancellation and report capture bounded.

Some devices do not expose useful public information and can remain unnamed. Physical recognition gains have not been measured. Scans remain user-started, with results held in memory and no cloud recognition. SNMP remains follow-up work; the IPv6 multicast experiment is not enabled in normal scans.

## 0.3.34 — A clearer view of your network

- Bring the network, scan action, address-check progress and scan status into one card.
- Browse individual device cards with larger icons. Search reported details or use quick filters for unnamed devices, network equipment, smart home, computers and media.
- See grouped IPv4 and IPv6 addresses, copy individual addresses, and expand recognition explanations, audio information and source-labelled observations in place.
- Use a two-pane list and details layout on wider screens. Keep scalable text, both themes, accessible controls and your list position when returning from details.
- Move scan details into a bottom sheet and keep update progress and Cancel visible when Wi-Fi changes.
- Remove the manual Envisalink check and its dedicated TPI reader, which could compete with an alarm integration’s single connection.
- Keep report sharing cancellable when a recipient stops reading, including on Android 8. Exclude scan text from automatic content capture while preserving screenshots, accessibility and explicit copy/share.
- Prevent an uncaught worker exception when a scan ends during device-detail work.

The interface now uses Jetpack Compose. Discovery and recognition rules remain in the existing backend; this release does not claim to identify previously silent devices.

## 0.3.33 — Safer checks and troubleshooting reports

- Remove the Envisalink TPI probe from normal device checks. **Show technical details → Check for Envisalink** explains the possible integration conflict and starts a fresh scan with that check requested for only the selected address.
- Mask additional IPv6 addresses embedded in retained report text, including compressed, scoped and IPv4-mapped forms. Preserve useful firmware versions and the explicit option to include names and addresses.
- Hide sensitive clipboard previews for copied device details and troubleshooting pages on supported Android versions.
- Build report-name matching once per report and reuse repeated text, reducing preparation work on larger inventories.
- Give SSDP senders separate processing and record budgets, so a burst from one device does not immediately exhaust discovery for the others. Keep explicit overall time, packet and record limits and report any limit reached.

Physical alarm-integration compatibility and report performance on phones still require testing.

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

Update through **Options → Check for updates** or download the APK from [GitHub Releases](https://github.com/kazands/network-lens/releases/tag/v0.3.39).

[Verification for this release](VERIFICATION-0.3.39.md) · [Earlier releases](https://github.com/kazands/network-lens/releases) · [Documentation](README.md)
