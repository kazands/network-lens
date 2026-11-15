# Development status

Network Lens 0.3.14 is the current Android development preview. Download its
signed APK, matching source, checksums and [verification notes](VERIFICATION-0.3.14.md)
from [GitHub Releases](https://github.com/kazands/network-lens/releases/tag/v0.3.14).
Android 8.0+ is supported; the application targets API 37. It remains a focused,
user-started Wi-Fi device finder with all scan state held in memory.

## Current functionality

- Start a fresh foreground scan on the selected Wi-Fi, search one device list,
  and inspect source-labelled observations for each IPv4/IPv6 endpoint.
- Discover current responders through ICMP, bounded TCP checks, mDNS/DNS-SD,
  SSDP/guarded UPnP, NetBIOS, read-only SADP, WS-Discovery and PS5 discovery.
  Cached/native advertisements are candidates, never proof of presence.
- Display supported canonical product names first, with useful custom names
  underneath. Details and explicit copies preserve raw names, reported models,
  manufacturers, recognition inputs/sources and IEEE interface vendors separately.
- Recognize exact current manufacturer/model reports through a free offline
  catalogue: **86,540 product/family titles across 5,263 brands**, indexed by
  **256,338 qualified aliases**. It covers worldwide hardware beyond Android.
  The pinned public sources, provenance, filtering and precision are documented
  in [device identities](DEVICE-IDENTITIES.md).
- Interpret 156 exact current Apple model codes through the pinned
  [Recog subset](RECOG.md). Reviewed recorder, streaming, console and thermostat
  profiles select more specific Lucide icons, including **Sony PlayStation 5**
  and **ecobee Smart Thermostat Premium**. An unmatched audio capability is
  labelled **Audio service** rather than a hardware category.
  Unknown form factors keep categories justified by observed services.
- Read bounded, source-labelled [local metadata](LOCAL-METADATA.md) from Thread,
  ESPHome, Shelly, WLED, Spotify Connect and IPP services. Lookup outcomes and
  current alternative observations remain visible per endpoint.
- Preserve light/dark appearance, scalable native text, IPv6 below IPv4,
  conservative grouping, exact address copying and Back/list-position behavior.
- Report Complete or Partial with source outcomes, failures and work limits.
  Stop, cancellation and network changes reject late updates and clean up work.

A current response is required for presence. Missing model, manufacturer and MAC
values stay unknown. HomeKit pairing IDs are not hardware MACs. Conflicting
identity reports suppress recognition for the scan, including after visible
observation eviction. Progress copies are independent; every new scan starts fresh.

There is no saved inventory, scan history, persistent Wi-Fi fingerprint, account,
analytics, cloud recognition or background scanning. The catalogue is a bundled
snapshot, not a live service; an online catalogue updater is not implemented.
An optional future public-data download can keep recognition local without
uploading device identifiers.

## Verification

The category corrections passed all 26 host suites / 29,949 checks and 344
identity/presentation checks on an isolated API 37 emulator. Synthetic fixtures
cover the Sony Spotify brand spelling, exact Ecobee maker/model reports,
conflicts, raw-field separation, dual-stack grouping, icons, search, accessibility
labels, both appearances and normal/200% text at 320dp/411dp.

Versioned release checks and their exact scope are recorded in
[release verification](VERIFICATION-0.3.14.md). Earlier release checks remain in
[0.3.13 verification](VERIFICATION-0.3.13.md) and the
[implementation verification history](archive/IMPLEMENTATION-VERIFICATION.md).
A read-only phone inspection informed these corrections; the corrected APK still
needs physical-device verification. Emulator checks do not establish household
recognition accuracy or complete discovery.

## Known limits

A scan cannot guarantee a complete physical inventory. Sleeping devices,
filtering, multicast delivery, network isolation and Android restrictions affect
coverage. A catalogue entry does not guarantee that the device exposes enough
current data to identify it. Broad sources do not reliably establish form factor,
retail revision, edition or ownership.

Intermittent HomeKit (`_hap._tcp`) and HTTP (`_http._tcp`) address-resolution
failures remain unresolved. Android can register a service without supplying a
usable address within the bounded attempts. Other methods continue and the scan
correctly stays Partial. Longer timeouts and alternate resolver experiments have
not established a production fix.

Some advertised Spotify Connect endpoints time out or refuse connections while
other endpoints return valid metadata. These failures remain explicit; longer or
repeated connections have not demonstrated recovery. Requested metadata failures
remain failed, and exhausted work/deadlines remain incomplete.

Physical PS5 discovery, household recognition accuracy, physical TalkBack, phone
performance, broader phone/router compatibility and real Thread-mesh coverage
remain unverified for this release. The release checks used isolated emulators
and did not operate a phone. Passing parser, reporting, layout or lifecycle checks
does not prove successful discovery on real hardware.

Google Play enrollment, listing/privacy declarations and a verified App Bundle
pipeline remain separate future work. GitHub previews gather testing feedback;
they do not establish production readiness.
