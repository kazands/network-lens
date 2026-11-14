# Development status

Network Lens 0.3.13 is the current Android development preview. Download its
signed APK, matching source, checksums and [verification notes](VERIFICATION-0.3.13.md)
from [GitHub Releases](https://github.com/kazands/network-lens/releases/tag/v0.3.13).
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
  [Recog subset](RECOG.md). Reviewed recorder, streaming and console profiles
  select more specific Lucide icons, including **Sony PlayStation 5**.
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

The merged feature and its response-order corrections passed all 26 host suites
/ 29,914 checks, including a fresh independent Deep review run. That final review
approved both corrections without substantive findings. Initial feature matrices
covered API 26/36/37; the final logic corrections passed 300 identity checks on
API 26 and four modes / 678 checks on API 37.

For the versioned download, both direct builds and package audits passed, along
with 116 isolated signing assertions, 300 API 37 identity checks and 21 checks
updating the exact public-signed APK from the previous public release. The app UID
and nonempty runtime grants were retained. Development/public application payloads
match; only versioned manifest metadata differs from the reviewed feature APK.
Exact hashes, test setup corrections and platform scope are in
[release verification](VERIFICATION-0.3.13.md). Earlier candidate hashes and
implementation-specific counts remain in the
[verification history](archive/IMPLEMENTATION-VERIFICATION.md).

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
