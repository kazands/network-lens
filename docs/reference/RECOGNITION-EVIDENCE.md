# Recognition evidence and compatibility

This describes the broader recognition implementation in source. It does not mean
that an already installed preview contains these changes. Physical-device results
must be checked on a build containing them before claiming a device is fixed.

## Three separate decisions

1. **Product identity:** an exact manufacturer/model lookup or reviewed model code
   gives a canonical product or family title. All specific model reports must be
   compatible; unknown aliases and genuine contradictions can suppress that title.
2. **Primary physical type:** reviewed product-model evidence, explicit HomeKit
   categories, supported UPnP device descriptions and surveillance observations are
   resolved independently. An unresolved retail name does not turn a known
   thermostat into a speaker. Contradictory physical types remain explicit.
3. **Service capabilities:** AirPlay, RAOP, Spotify Connect, Cast, printing, file
   sharing, Apple Companion Link, HomeKit, Matter and Thread are shown separately.
   These services do not establish a retail product, manufacturer or ownership.

This-phone and Wi-Fi gateway roles retain precedence. A recorder or doorbell can
refine a broad camera/surveillance classification. Other incompatible categories
produce **Conflicting device types** rather than whichever icon branch ran first.
Service fallbacks have a stable order across IPv4/IPv6 endpoints.

Each explicitly reported physical category keeps up to four source labels; catalogue
models retain their existing bounded evidence separately. Distinct category conflicts
survive eviction from the eight-observation display list and progress copies.
All state remains in the current scan's memory. A fresh scan starts without it.

The broad catalogue still contains 256,338 manufacturer-qualified aliases, 86,540
product/family titles and 5,263 brand keys. It is a naming catalogue, not 256,338
verified hardware fingerprints. No new paid database, cloud recognition, packet
upload, saved network history or manufacturer inference from IEEE vendor was added.

## Confirmed failures and changes

| Failure | Resulting behavior |
| --- | --- |
| Corporate manufacturer aliases and protocol-specific model aliases suppressed a valid thermostat match | Reviewed `ecobee` / `ecobee Inc.` / `ecobee Inc` and Premium / `ECB601` reports agree while raw values stay unchanged |
| An unsupported secondary model erased useful physical classification | Exact retail identity stays conservative; independently supported category remains available |
| Group product IDs depended on the input alias | Global identities use the canonical brand/title; grouped addresses can combine complementary manufacturer/model evidence |
| HomeKit TV and media-player categories collapsed together and audio fallback could win | TV category 31 is distinct from Apple TV / set-top-box / streaming-stick categories 24/35/36; all known HomeKit categories participate in physical-type resolution |
| Samsung extensionless UPnP locations were rejected | Same-responder HTTP `/smp_` plus one to five decimal digits plus `_` locations are accepted alongside existing paths |
| UPnP model descriptions were ignored for classification | Root `modelDescription` is retained; specific Samsung TV types/descriptions qualify a television, and standardized camera/light types qualify their respective categories |
| A closed first optional TCP port ended all later checks | Refusal no longer prevents later probes in the existing bounded port list; the first successful connection still ends this optional check |
| Copy/search omitted some source observations | Copy includes source-labelled observations/type evidence and UPnP status; search includes raw observation values and capability names |
| Apple model mapping required a complete device-info service | Reviewed current Companion Link, AirPlay and RAOP model fields can use the exact mapping with actual-source provenance |
| TXT-only Bonjour model records could not qualify | A current same-sender, exact-instance complete service can anchor the auxiliary model record without inventing a listening port |
| Apple table omitted many iPad/iPhone generations | A pinned MIT-licensed AppleDB supplement adds 128 exact released mobile identifiers and 179 unique mobile board aliases, retaining the existing conservative Recog mappings |

## Samsung compatibility boundary

The PN59D550 family has a manufacturer-qualified profile. The support page and its
English manual establish this older television's AllShare/DLNA capability; a modern
Tizen REST API is not assumed. The app does not initiate remote-control pairing,
SOAP control actions, subscriptions or account requests.

The description reader continues to require a numeric host equal to the current
SSDP responder, eligible Wi-Fi scope, credential-free HTTP, no redirect and no
fragment. The new `/smp_N_` path does not permit supplied query actions, encoded
variants, traversal or another host. Existing request/body/parser/deadline limits
remain. Only the advertised description location is requested; service control
URLs inside its XML are never followed.

`MediaRenderer` and `MediaServer` alone are not televisions. A Samsung manufacturer
must corroborate an explicit supported Samsung TV description/type. Generic
Samsung renderers, soundbars and PC media servers do not acquire a TV category.
Root descriptions for standardized UPnP `DigitalSecurityCamera:1` and
`DimmableLight:1` are also interpreted without a brand-specific product list.

Unsupported location, request-limit and fetch outcomes appear in the affected
endpoint's details. These explain missing identity information without claiming
that a present device is offline. A missing model in a previous user's scan has
not been tied conclusively to a particular description URL or transport failure.

## Apple compatibility boundary

The included [direct Apple model lookup](APPLE-RECOGNITION.md) complements the
advertisement paths below. It validates the service before requesting only
ProductType and retains the same catalogue, conflicts and fresh-scan boundaries.

See [Recog input rules](RECOG.md#additional-current-apple-model-sources). The adapter
uses exact retained Recog and AppleDB model mappings; it does not infer a generation from an Apple-like
name or turn Companion Link's private identifiers into MAC addresses.

Auxiliary model queries use the existing four-question batch, 256-question-key and
two-attempt limits. Eligibility requires a complete current Companion Link,
AirPlay, SMB or workstation service from the same sender with the exact same
instance label. No PTR/SRV is required for the auxiliary TXT itself. TTL expiry,
goodbyes, conflicts, cancellation and fresh-scan isolation remain enforced.

Auxiliary questions and associations preserve the original DNS instance label,
including leading/trailing spaces and UTF-8. Display-name cleanup is never used
to construct protocol identities. Regression coverage includes whitespace-only
names, names that look alike after trimming, and continued Android multicast
reception after querying such an instance.

Some devices expose Companion Link but withhold their model. They retain their
reported name and **Apple companion service** label. Manufacturer/model fields
remain unknown unless reported. Merely expanding a database cannot recover a
model that was never received, and this change does not promise that every iPhone
or iPad will disclose one.

The supplemental table includes the tenth-generation iPad Wi-Fi and cellular
identifiers separately. This addresses a confirmed catalogue omission; it does
not prove that a particular iPad advertises its model during a scan. Comparison
with another scanner's correct title does not establish that scanner's inputs.

## Research and provenance

Research conducted on 2026-11-15. Protocol patterns were independently implemented;
no third-party executable code was copied. Public AppleDB device facts are retained
under MIT with their notice and upstream credits; only reviewed mobile mappings
are compiled into the application.

- [AppleDB](https://github.com/littlebyteorg/appledb) and [Apple iPad identification](https://support.apple.com/en-us/108043): exact mobile identifiers and names, MIT notice and upstream credits retained. The tenth-generation Wi-Fi model is A2696; the reviewed AppleDB mapping associates it with `iPad13,18`.
- [Home Assistant Samsung flow](https://github.com/home-assistant/core/blob/fddf359f543346a9b7cc0068e8e4dc19944dea13/homeassistant/components/samsungtv/config_flow.py): candidate discovery is followed by device-specific validation. Apache-2.0 source inspected; its modern API assumptions and persistent registry are not adopted here.
- [pyatv Companion protocol](https://pyatv.dev/documentation/protocols/#companion-link) and [pinned mDNS parser](https://github.com/postlund/pyatv/blob/b277a4c8222ecdcbaab8a24e3e713ca44765adb4/pyatv/core/mdns.py): `rpMd` is a model field; device-info TXT can exist without SRV. MIT source inspected. Network Lens retains its stricter current-sender association.
- [HAP-NodeJS category definitions](https://github.com/homebridge/HAP-NodeJS/blob/latest/src/lib/Accessory.ts): category values inspected, file blob `ed3f346f437341bb160fd83590f12aa0276586cf`. These are reported UI categories, not authenticated hardware identity.
- [Samsung developer capture](https://github.com/casperboone/homey-samsung-smart-tv/blob/master/samsung.md): `/smp_2_` device description, root model/type fields; file blob `99e524f28b30f3fb2bbeb185cc381b4abdbae7d9` inspected. This establishes a compatibility example, not the affected PN59D550's actual location.
- [Samsung PN59D550 support](https://www.samsung.com/ca/support/model/PN59D550C1FXZC/): manufacturer/model and linked AllShare manual inspected. Public manual required direct official retrieval because the web reader could not open it.
- [ecobee Premium](https://www.ecobee.com/en-us/smart-thermostats/smart-thermostat-premium/): thermostat plus voice/music capabilities. Exact dual-protocol aliases were corroborated by authorized current device inspection; tests use synthetic endpoints and names, not household scans.
- [UPnP camera specification](https://upnp.org/specs/ha/UPnP-ha-DigitalSecurityCamera-v1-Device.pdf) and [UPnP dimmable-light specification](https://upnp.org/specs/ha/UPnP-ha-DimmableLight-v1-Device.pdf): standardized device URNs inspected. Control functionality is not implemented.

Rejected as drop-in recognition databases: NIST CPE is a naming dictionary;
Matomo's television rules expect browser User-Agent inputs; wider Recog service
fingerprints can describe software rather than the hardware hosting it. IEEE
identifies the network-interface registrant. None is a universal LAN device list.
Fing/Nmap code or databases were not incorporated.

## Verification

`HardwareClassificationTest`, `MdnsAssemblyTest` and `RecogTest` exercise the shared
resolver, source adapters, synthetic wire association, malicious/unsupported
locations, contradictory evidence, ordering, bounds and fresh-scan isolation.
`RecognitionEvidenceChecks` runs inside the emulator-guarded `device-identities`
mode and covers production Device/Discovery integration. Existing list/details,
copy, search, large-text, theme and navigation checks remain in that mode.

Run the direct commands in [BUILDING](../BUILDING.md). Passing synthetic checks is not
proof of real-device discovery, performance or model disclosure. Actual installed
version, current response and per-source evidence must be checked before claiming
an individual device is fixed. In-app update checking remains a separate task.

### Development candidate checked on 2026-11-15

- Direct host tests: **27 suites / 30,568 checks passed**.
- Direct development APK build and its permission/security checks passed.
- Isolated API 37 emulator: **964 passing checks** in one completed matrix:
  device-identities (472), recog (298), homekit (69), local-metadata (49),
  split-mdns (24), fresh-presence (31) and resolver-upgrade (21).
  The installed application hash was verified.
- The DNS-label regression was demonstrated failing against the earlier
  implementation before the fix. The host suite now covers all four auxiliary
  anchor types, IPv4/IPv6, whitespace-only, leading/trailing-space, embedded-dot
  and UTF-8 labels. Distinct raw names cannot borrow each other's model records.
- Android's actual multicast loop sent the whitespace-name question, remained
  alive, and received a later ordinary response that supplied the model. The
  fixture first asserts that no model is present, avoiding a false-positive
  receiver-continuity check.
- Synthetic normal/200% text, 320dp/411dp and light/dark layout checks passed.
  Thermostat details in light and large-text dark appearances were visually
  inspected on the preceding candidate; presentation code did not change in the
  DNS-label correction. No household scan or personal screenshot was saved in
  the repository.
- Generated-data consistency and current working-tree repository hygiene passed.
- A fresh independent Deep Kaz review of the complete unpublished scope returned
  **Approve**, with no substantive findings. The final instrumentation follow-up
  was also checked. This is source review, not physical-device certification.

Application SHA-256: `3135cfae27675ca87969e3587cfff236bec99e51d08c464e9ad0eb0bcabcb796`.
This is a development build still bearing the unchanged 0.3.14 version label,
**not** the published, release-signed 0.3.14 artifact. It was not installed on a
physical phone or published. API 26/36, physical TalkBack, phone performance and
fresh device model disclosure remain unverified for this candidate. Samsung's
actual missing-model cause has not been confirmed.

## Published preview verification

The versioned 0.3.15 release checks and exact artifact hashes are recorded in
[release verification](../archive/VERIFICATION-0.3.15.md). The development candidate above
remains a historical verification record.

## TP-Link plugs and switches (0.3.18)

Dedicated UDP discovery now obtains model evidence from supported Kasa/Tapo
plug and switch replies. Exact reviewed models distinguish smart plugs, light
switches and dimmers; a generic smart-plug/switch service family does not.
See [protocol coverage, safeguards and limitations](TP-LINK.md).
