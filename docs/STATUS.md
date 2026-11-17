# Development status

Network Lens 0.3.18 is the current Android development preview. Download its
signed APK, matching source, checksums and [verification notes](VERIFICATION-0.3.18.md)
from [GitHub Releases](https://github.com/kazands/network-lens/releases/tag/v0.3.18).
Android 8.0+ is supported; the application targets API 37. It remains a focused,
user-started Wi-Fi device finder with all scan state held in memory.

## New in this preview

[TP-Link discovery](TP-LINK.md) adds read-only Kasa/Tapo plug and switch replies
over UDP 9999/20002, with 32 reviewed model mappings. Smart plugs, wall switches
and dimmers receive distinct labels and matching icons. No account or device
control is required.

## In-app updates

[Check for updates](APP-UPDATES.md) finds newer public releases, including previews,
downloads and verifies the APK, and asks Android to install it. The check is
explicit; no background checking or scan-data upload is introduced.

## Included recognition improvements

[Apple service model lookup](APPLE-RECOGNITION.md) adds a bounded, read-only
ProductType request to current eligible Apple service endpoints without pairing.
[Android/Cast recognition](ANDROID-RECOGNITION.md) reads nested receiver metadata
and adds 651 manufacturer-qualified hardware-category pairs. Virtual playback
group identity stays separate from physical receiver identity.

[Audio support](AUDIO-ZONES.md) adds VSSL amplifier/zone mapping, Sonos topology,
HEOS player/groups, MusicCast hardware zones/distribution, per-port BluOS players
and current AirPlay/Cast group reporting. Group membership cannot create devices,
supply a neighbour's model or merge physical hardware. Cast HTTP group descriptions
do not establish speaker membership. All lookups are bounded and local.

Earlier physical-type corrections for Ecobee Premium, Samsung description
compatibility and the 284-code Apple model catalogue remain. The cause of the
original missing Samsung model was not confirmed. Fresh physical testing remains
necessary.

## Current functionality

- Start a fresh foreground scan on the selected Wi-Fi, search one device list,
  and inspect source-labelled observations for each IPv4/IPv6 endpoint.
- Discover current responders through ICMP, bounded TCP checks, mDNS/DNS-SD,
  SSDP/guarded UPnP, NetBIOS, read-only SADP, WS-Discovery, PS5 and TP-Link discovery.
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

All 33 host suites passed, including 797 TP-Link checks. Before release versioning,
API 37 passed 714 checks across device identities, HomeKit, scan efficiency and
session-only/cold-start behavior. Independent Deep review approved after a
self-only scan-status correction. The production host parser identified an
authorized physical HS103 through both supported UDP formats; physical dimmer
and on-phone discovery compatibility remain unverified.

Exact release artifact and upgrade checks are recorded in
[0.3.18 verification](VERIFICATION-0.3.18.md). Earlier updater and recognition
results remain in the verification archive.
Synthetic fixtures do not establish household recognition accuracy.

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
