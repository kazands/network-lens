<p align="center">
  <img src="docs/images/network-lens-logo.png" alt="Network Lens logo: a teal Wi-Fi magnifying glass on frosted mint glass" width="160" height="160">
</p>

# Network Lens

A focused Android Wi-Fi device finder, written in Kotlin. Start a scan, search the devices that responded, and inspect their IPv4/IPv6 addresses, available MAC addresses and reported details.

[![Download Android APK — Network Lens 0.3.8 preview](docs/images/download-apk.svg)](https://github.com/kazands/network-lens/releases/download/v0.3.8/Network-Lens-0.3.8.apk)

[Release notes, source and checksums](https://github.com/kazands/network-lens/releases/tag/v0.3.8) · No GitHub account required to download.

**Development preview: 0.3.8.** Android 8.0+; target SDK 37. Network Lens is not yet a production release on Google Play.

The current source is 0.3.10 and uses the Android application ID `io.github.kazands.networklens`. It installs as a separate app from the older preview linked above; it cannot update that installation in place. See [the identity change](docs/IDENTITY-MIGRATION.md) before switching.

Download the APK, source package, `SHA256SUMS` and verification notes from the same release. Install the APK on your Android phone after checking its checksum. The preview uses a production app-signing identity; an older test-signed installation requires a one-time uninstall/reinstall. Later maintainer previews retain that app-signing identity. Private signing credentials are never distributed.

Read the [0.3.8 verification notes](docs/VERIFICATION-0.3.8.md) for completed checks and remaining limitations. Reports from other phones and routers are welcome; remove device names, addresses and other private identifiers before opening an issue.

## What it does

- User-started, foreground discovery on the selected Wi-Fi connection.
- ICMP-first discovery with retries, bounded TCP fallback, mDNS/DNS-SD, SSDP/guarded UPnP, NetBIOS, read-only SADP and WS-Discovery.
- Current responses establish presence. Cached advertisements remain candidates; router PTR/DHCP names are not used for identity.
- Conservative grouping, IPv4 with IPv6 below it, searchable results, source-labelled device details and copy actions.
- Dark and light appearances, accessible text scaling, Lucide device pictograms and Back navigation that preserves list position.
- Complete/Partial scan status with per-method results and reasons. IPv6 discovery uses advertised addresses and eligible local routes; it does not enumerate the IPv6 address space.
- Offline IEEE prefix lookup for explicitly reported MAC addresses. An interface supplier is kept separate from a device-reported manufacturer.

Results, filters, appearance and range choices stay in memory. Every scan starts fresh. A process restart requires a new scan. There is no saved inventory, history, cloud recognition, account, advertising, analytics, monitoring or background scanning. Explicit copy actions use Android's clipboard.

## Limits

A scan cannot guarantee a complete physical inventory. Sleeping devices, filtering, multicast delivery, network isolation and Android restrictions affect coverage. MAC addresses remain unknown unless available from a supported source. Device names and metadata are self-reported.

Intermittent HomeKit (`_hap._tcp`) and web-service (`_http._tcp`) address resolution remains an open issue. Version 0.3.7 improves error reporting; it does not claim to fix that discovery limitation. See [development status](docs/STATUS.md).

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

The manifest declares Internet, network state, Wi-Fi state, multicast state, fine/coarse location and local-network permissions. Location access is optional and only used to show the connected Wi-Fi name; the app does not request coordinates. Local-network approval is required on Android versions that enforce it. There is no Bluetooth, camera, microphone, contacts, background location or storage permission.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and [the project requirements](tests/E2E-REQUIREMENTS.md). Keep the app focused on reliable fresh discovery and clear device information. Use only networks and devices you are authorized to test.

This repository contains app source, tests, build scripts, assets and public documentation. Local tool caches, signing material, household captures, reverse-engineering artifacts and historical build outputs are excluded.

## License and attribution

Copyright (C) 2026 Network Lens contributors.

Network Lens's original source code, documentation and original artwork are licensed under **GNU GPL version 3 only (`GPL-3.0-only`)**. See [LICENSE](LICENSE). Contributions are accepted under the same license; no separate contributor agreement is required.

Third-party components and registry data retain their own terms and attribution. See [NOTICE](NOTICE) and [android/THIRD_PARTY_NOTICES.md](android/THIRD_PARTY_NOTICES.md). The GPL does not grant trademark rights. This is an independent implementation; it contains no Fing code, assets, recognition database or cloud API, and no Nmap code or databases.
