# Development status

Network Lens 0.3.22 is the current Android development preview. Download its
signed APK, matching source, checksums and [verification notes](VERIFICATION-0.3.22.md)
from [GitHub Releases](https://github.com/kazands/network-lens/releases/tag/v0.3.22).
Android 8.0+ is supported; the application targets API 37. It remains a focused,
user-started Wi-Fi device finder with all scan state held in memory.

## Amazon Fire TV recognition in 0.3.22

Network Lens reads WhisperPlay friendly names and recognizes a
reviewed Fire TV Stick 4K device-type code offline. It adds explicit Amazon
service queries, targeted DIAL searches and the documented Fire TV description
URL format. Generation remains unspecified; missing reported fields stay unknown.
Known Amazon hardware evidence also prevents conflicting Apple service models
from taking over the product identification, including across grouped addresses.
Conflicting model evidence cannot restore a definite physical device type through
another model catalogue or a neighboring grouped address.
See [Fire TV recognition](FIRE-TV-RECOGNITION.md) for research, attribution,
passed tests and remaining physical-phone verification. These changes are
included in 0.3.22; see the [release verification](VERIFICATION-0.3.22.md).

## Scan reliability retained from 0.3.20

Stopping a scan or leaving the app closes discovery resources safely, including
when cancellation happens during startup. Multicast reception cannot remain held
because a cancelled scan starts late. Device-list updates use detached snapshots
and retain the existing session and ordering checks.

Automatic Wi-Fi ranges above 1,024 addresses continue service discovery without
an individual address sweep or a hidden change to a smaller subnet. Scan details
record the omitted checks. A custom smaller range still supports an address sweep.

The EAP page parser handles HTML comments in linear time, and technical details
show the complete explanation of its credential-free identity request. Complete
multicast DNS responses remain usable when the sender sets the TC flag; malformed
packet checks are preserved. Installer-session opening failures produce a
recoverable update error.

Scan resource ownership, selected protocol adapters, metadata plans and UI
snapshots have clearer boundaries and dedicated regressions. See
[scan architecture](SCAN-ARCHITECTURE.md) and
[recognition and performance baselines](RECOGNITION-BASELINE.md).

## Included discovery and recognition

Network Lens includes [Omada announcements and public EAP identity](OMADA.md),
plus [broader local recognition](DISCOVERY-COVERAGE.md): 348 pinned Home Assistant
rules across 122 mDNS types, 5,479 offline Matter registry entries, fair bounded
mDNS scheduling beyond the former 48-type ceiling, Easy Smart discovery,
Samsung TV identity, WiZ, Yeelight, Govee, LIFX and receive-only Tuya announcements.
Generic integration matches, service capabilities and module IDs remain separate
from verified hardware models. These capabilities remain included in 0.3.22.

Physical host checks identified EAP245 and EAP265 HD through public login
metadata; no Omada announcements were observed. The new Easy Smart path and other
added methods still need physical Wi-Fi compatibility checks. The authenticated
switch web page did not disclose a model; that finding does not establish whether
its UDP discovery path works.

Verification results for this source update are recorded in
[broader recognition coverage](DISCOVERY-COVERAGE.md#verification) and the
[subsequent pre-PR verification](PRE-PR-VERIFICATION.md), including failed full-run
checks, successful retries and remaining physical-test boundaries.
The [metadata and permission follow-up](METADATA-FOLLOWUP.md) records subsequent
recovery fixes, evidence retention and fresh regression/review results.

## Home-user interface improvements

The device-name fallback is **Unnamed device**. Once a scan ends, the main status
is **Scan finished**, or **Scan stopped** when cancelled. Specific failed checks,
coverage and limits remain in Scan details. The internal completeness state and
per-method failures are unchanged; the main label describes the scan lifecycle.

Device details lead with the physical type and recognition explanation. Raw
names, model codes, service-reported types, lookup outcomes and source evidence
are available under **Show technical details**, and remain in copied details.
Unidentified results and incomplete scans include practical next-step guidance.
Main actions now expose native button accessibility roles. See
[interface terminology](INTERFACE.md) for wording and scope.

## Existing TP-Link plug and switch support

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

## Current release verification

See [0.3.22 verification](VERIFICATION-0.3.22.md) for release artifact identities,
versioned checks, signed update verification and remaining limits. All 44 host
suites, seven offline Python tests and a fresh independent Deep code review passed
on the implementation.
Historical candidate checks are linked above and are not claims about every
physical device or firmware version.

## Known limits

A scan cannot guarantee a complete physical inventory. Sleeping devices,
filtering, multicast delivery, network isolation and Android restrictions affect
coverage. A catalogue entry does not guarantee that the device exposes enough
current data to identify it. Broad sources do not reliably establish form factor,
retail revision, edition or ownership.

Intermittent HomeKit (`_hap._tcp`) and HTTP (`_http._tcp`) address-resolution
failures remain unresolved. Android can register a service without supplying a
usable address within the bounded attempts. Other methods continue and the scan
retains incomplete per-method coverage. Longer timeouts and alternate resolver experiments have
not established a production fix.

Some advertised Spotify Connect endpoints time out or refuse connections while
other endpoints return valid metadata. These failures remain explicit. The new bounded retry and alternate-target paths
do not establish recovery on every physical device. Requested metadata failures
remain failed, and exhausted work/deadlines remain incomplete.

Physical PS5 discovery, household recognition accuracy, physical TalkBack, phone
performance, broader phone/router compatibility and real Thread-mesh coverage
remain unverified for this release. The release checks used isolated emulators
and did not operate a phone. Passing parser, reporting, layout or lifecycle checks
does not prove successful discovery on real hardware.

Google Play enrollment, listing/privacy declarations and a verified App Bundle
pipeline remain separate future work. GitHub previews gather testing feedback;
they do not establish production readiness.

## Recognition planning

Independent identity probes, bounded alternate metadata targets, endpoint-fair
metadata scheduling and a labelled synthetic benchmark are described in
[recognition planning](RECOGNITION-PLANNING.md). These features remain included in 0.3.22.

## Google device recognition in 0.3.21

Current Model values such as `Google Nest Mini`, `Google Home` and `Google Home
Mini` now have exact first-party product profiles even when Manufacturer was not
reported. Fifteen reviewed profiles cover speakers, smart displays, streaming
receivers, Chromecast Audio, the Nest Wifi point and Pixel Tablet. Product names
lead presentation; useful room names remain underneath. Smart displays have a
distinct physical category, and Cast capability does not make every receiver a
speaker or a Google product. Missing reported manufacturer/MAC fields remain
unknown. See [Google model rules and sources](DEVICE-IDENTITIES.md#google-cast-models).

These changes are included in 0.3.22. See [release verification](VERIFICATION-0.3.22.md)
for the versioned artifacts, update checks and remaining physical-test limits.

Before release versioning, all 43 Kotlin host suites and seven offline Python tests passed,
including 285 new Google product checks. The direct application and test builds
passed. Focused Google checks passed on isolated API 26 and API 37 emulators
(158 each); API 37 also passed the broader device-identity presentation suite
(855) and scan-efficiency checks (22). Each run verified the installed app and
test APK hashes. The new host regression fails against the preceding source.
These synthetic results do not establish compatibility with every physical
receiver or its firmware; the updated presentation still needs phone testing.

## Implementation history

The [review follow-up](REVIEW-FOLLOWUP.md) records pre-versioning candidates,
regressions and corrections included in 0.3.20. Its older APK hashes and test
counts remain historical evidence; use the current release verification for the
published artifact. Automated CI was excluded from this release work.
