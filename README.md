<p align="center">
  <img src="docs/images/network-lens-logo.png" alt="Network Lens logo: a teal Wi-Fi magnifying glass on frosted mint glass" width="160" height="160">
</p>

# Network Lens

A focused Android Wi-Fi device finder, written in Kotlin. Start a scan, search the devices that responded, and inspect their IPv4/IPv6 addresses, available MAC addresses and reported details.

[![Download Android APK — Network Lens 0.3.22 preview](docs/images/download-apk.svg)](https://github.com/kazands/network-lens/releases/download/v0.3.22/Network-Lens-0.3.22.apk)

[Release notes, source and checksums](https://github.com/kazands/network-lens/releases/tag/v0.3.22) · No GitHub account required to download.

**Development preview: 0.3.22.** Android 8.0+; target SDK 37. Network Lens is not yet a production release on Google Play.

This preview improves **Amazon Fire TV recognition**. Supported current Amazon
advertisements show useful device names instead of internal identifiers. A
reviewed public device-type code identifies **Amazon Fire TV Stick 4K**; other
qualifying advertisements retain the broader **Amazon Fire TV** label. Generation
is not inferred. Conflicting reports cannot override the physical identity with
an unrelated Apple or Google model. See [Fire TV coverage](docs/FIRE-TV-RECOGNITION.md).

The Google recognition improvements remain included: supported Home, Nest and
Chromecast receivers show a clear product name and physical device type, with
useful room names underneath. Shared model strings retain their family label.
See [Google model coverage](docs/DEVICE-IDENTITIES.md#google-cast-models).

The scan reliability improvements from 0.3.20 remain included: safe resource
cleanup, service discovery on larger Wi-Fi networks, bounded metadata parsing
and recoverable update-installer errors.

Local recognition includes Omada/EAP and Easy Smart network equipment, Samsung TV
identity, WiZ, Yeelight, Govee, LIFX and passive Tuya announcements. The bundled
catalogues include 348 Home Assistant matcher rules and 5,479 Matter product
records. See [discovery coverage](docs/DISCOVERY-COVERAGE.md) and
[recognition planning](docs/RECOGNITION-PLANNING.md) for support and limits.

Device details lead with physical identity and explain recognition. Raw reports
remain under **Show technical details**. The list uses **Unnamed device** when no
usable name/model was received, and **Scan finished** or **Scan stopped** for the
scan lifecycle; per-method failures and coverage remain in Scan details.

Use **Options → Check for updates** or **Settings → App updates** to download and
verify this APK inside Network Lens, then approve installation in Android.
See [in-app updates](docs/APP-UPDATES.md).

The earlier [Apple](docs/APPLE-RECOGNITION.md), [Android](docs/ANDROID-RECOGNITION.md)
and [audio](docs/AUDIO-ZONES.md) recognition improvements remain included.

The public APK updates existing public-signed Network Lens installations in place. It retains the application ID `io.github.kazands.networklens` and the pinned app-signing identity. See [application identity and updates](docs/APPLICATION-IDENTITY.md) for build and installation details.

Download the APK, source package, `SHA256SUMS` and verification notes from the same release. Install the APK on your Android phone after checking its checksum. The preview uses the pinned production app-signing identity. Future maintainer previews under this application ID retain that identity. Private signing credentials are never distributed.

Read the [0.3.22 verification notes](docs/VERIFICATION-0.3.22.md) for completed checks and remaining limitations. Reports from other phones and routers are welcome; remove device names, addresses and other private identifiers before opening an issue.

If you already have 0.3.17 or newer, use Check for updates. Older installations need the download link once to receive the updater.

## What it does

- User-started, foreground discovery on the selected Wi-Fi connection.
- ICMP-first discovery with retries, bounded TCP fallback, mDNS/DNS-SD, SSDP/guarded UPnP, NetBIOS, read-only SADP and WS-Discovery.
- Current responses establish presence. Cached advertisements remain candidates; router PTR/DHCP names are not used for identity.
- Conservative grouping, IPv4 with IPv6 below it, searchable results, source-labelled device details and copy actions.
- Dark and light appearances, accessible text scaling, Lucide device pictograms and Back navigation that preserves list position.
- Scan finished/stopped status with per-method results, coverage and failure reasons. IPv6 discovery uses advertised addresses and eligible local routes; it does not enumerate the IPv6 address space.
- Service discovery on automatic Wi-Fi ranges above 1,024 addresses. Individual address checks are skipped for these ranges and recorded in Scan details; select a smaller range when you need those checks.
- Offline IEEE prefix lookup for explicitly reported MAC addresses. An interface supplier is kept separate from a device-reported manufacturer.
- Source-labelled Thread/ESPHome fields and bounded local Shelly, WLED, Spotify Connect and IPP metadata queries. See [supported fields and limits](docs/LOCAL-METADATA.md).
- Canonical product names from a free offline catalogue covering 86,540 product/family titles across 5,263 worldwide brands, indexed by 256,338 exact manufacturer-qualified aliases. Useful custom names remain below the product title; raw fields and recognition sources remain in Details. See [catalogue coverage and evidence rules](docs/DEVICE-IDENTITIES.md).
- Offline Apple model interpretations for 156 Recog codes and 128 supplemental AppleDB iPad/iPhone codes, plus reviewed recorder, streaming, console, thermostat and television profiles. Supported identities select more specific Lucide icons; unknown categories remain based on observed services. See [Recog coverage](docs/RECOG.md) and [physical type versus service evidence](docs/RECOGNITION-EVIDENCE.md).
- Fifteen reviewed Google product profiles distinguish speakers, smart displays, streaming devices, audio receivers, a Wi-Fi point and a tablet. Product interpretation preserves raw reported fields and rejects contradictory evidence.
- Read-only Apple service model lookup, nested Cast receiver metadata and 651 manufacturer-qualified Android hardware categories. Playback services do not override a known physical device type.
- Source-labelled VSSL/Sonos, HEOS, MusicCast, BluOS and AirPlay/Cast audio relationships. BluOS players retain their advertised ports; playback groups never become physical hardware identity. Cast group descriptions do not establish speaker membership.
- Read-only TP-Link discovery on UDP 9999/20002 with 32 reviewed plug, switch and dimmer model mappings. Raw observations remain available; unknown models are not assigned a guessed physical category.
- Bounded read-only PS5 discovery, with supported evidence displayed as **Sony PlayStation 5**. Conflicting reports suppress recognition; names or IEEE vendors alone cannot establish a product.

Read [development status](docs/STATUS.md) for current functionality, verification and known limits.

Results, filters, appearance and range choices stay in memory. Every scan starts fresh. A process restart requires a new scan. There is no saved inventory, history, cloud recognition, account, advertising, analytics, monitoring or background scanning. Explicit copy actions use Android's clipboard.

## Limits

A scan cannot guarantee a complete physical inventory. Sleeping devices, filtering, multicast delivery, network isolation and Android restrictions affect coverage. MAC addresses remain unknown unless available from a supported source. Device names and metadata are self-reported.

Some HomeKit (`_hap._tcp`) and web-service (`_http._tcp`) advertisements still fail
to provide a usable address within the scan window. Scan details preserve those
failures and any incomplete coverage. The main status describes whether the scan
finished or was stopped. See [development status](docs/STATUS.md).

## Build and test

See [BUILDING.md](docs/BUILDING.md) for the supported direct build, pinned toolchain, tests and signing-key behavior. No private key or prior test inventory is required to build from a fresh checkout.

The application, host suites and Android instrumentation are Kotlin. The tests retain the existing standalone runners and emulator safety restrictions.

```sh
export JAVA_HOME=/path/to/jdk-21
bash scripts/bootstrap-sdk.sh
bash scripts/test.sh
bash scripts/build.sh
```

Outputs and downloaded tools are ignored by Git. A fresh checkout creates its own local development signing key, which cannot update an installation signed by a different key.

The maintainer's direct release build uses `bash scripts/build.sh --release`, a separate pinned app-signing key and verified recovery receipt. Its APK and audits go under `artifacts/release/`. Private credentials are never supplied by this repository. See [release signing](docs/RELEASE-SIGNING.md); Google Play enrollment and a verified App Bundle build remain separate work.

## Permissions

The manifest declares Internet, network state, Wi-Fi state, multicast state, fine/coarse location, local-network and request-package-install permissions. Location access is optional and only used to show the connected Wi-Fi name; the app does not request coordinates. Local-network approval is required on Android versions that enforce it. The install permission lets Android ask for approval when you request an app update. There is no Bluetooth, camera, microphone, contacts, background location or storage permission. Update APKs use temporary private cache; scan data and preferences remain in memory.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and [the project requirements](tests/E2E-REQUIREMENTS.md). Keep the app focused on reliable fresh discovery and clear device information. Use only networks and devices you are authorized to test.

This repository contains app source, tests, build scripts, assets and public documentation. Local tool caches, signing material, household captures, reverse-engineering artifacts and historical build outputs are excluded.

## License and attribution

Copyright (C) 2026 Network Lens contributors.

Network Lens's original source code, documentation and original artwork are licensed under **GNU GPL version 3 only (`GPL-3.0-only`)**. See [LICENSE](LICENSE). Contributions are accepted under the same license; no separate contributor agreement is required.

Third-party components and registry data retain their own terms and attribution. See [NOTICE](NOTICE) and [android/THIRD_PARTY_NOTICES.md](android/THIRD_PARTY_NOTICES.md). The GPL does not grant trademark rights. This is an independent implementation; it contains no Fing code, assets, recognition database or cloud API, and no Nmap code or databases.
