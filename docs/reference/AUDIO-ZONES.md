# Audio zones and physical hardware

Audio relationships are scan-local observations. They do not establish ownership,
authenticity or safety. Network Lens keeps zone endpoints and physical Sonos
players separate; a playback group is never an endpoint identity/grouping key.

## VSSL

A current `_airplay._tcp.` advertisement with the exact manufacturer `VSSL`
qualifies its responding endpoint for one read-only status query on TCP 50002.
The four request bytes are `10 00 01 08` (hex). The response must have command
`10 00`, a one-byte payload length, subcommand `08`, and valid JSON containing
string fields `id`, `tp`, and `mc`. No playback/settings command is sent.

Reviewed model codes: 1=A.1, 3=A.3, 6=A.6, 11=A.1x, 13=A.3x, 16=A.6x.
Single-zone devices report zone ID 7; the UI calls this Zone 1. Three/six-zone
models require an ID in their actual zone range. Unknown codes remain unsupported.

`mc` is the shared chassis/zone-one identifier. It is hashed in memory for
comparison, never assigned as another zone's MAC, never exported as a mapping
identifier, and never used to merge endpoints. Only independently responding zones
with matching chassis and model reports appear as related addresses. Different
models sharing an identifier produce a conflict instead of a mapping. Additional
addresses for the same zone do not become additional zones.

Exact manufacturer-qualified aliases such as `A6x`, `A.6x`, and `VSSL A.6x` use
**VSSL A.6x — Streaming amplifier**, retaining the advertised room name. A zone's
name, Cast UUID or Spotify playback role cannot establish the amplifier model.

## Sonos

A current same-peer UPnP description served on port 1400 must identify Sonos and
provide one valid root-device UDN. Network Lens then sends only the fixed
`GetZoneGroupState` SOAP action to `/ZoneGroupTopology/Control` on that same peer.
No subscription, cloud account, grouping change, playback action or update occurs.

The SOAP response must contain the responding player's own UDN in an active
`ZoneGroup`. `ZoneGroupMember` and nested `Satellite` elements describe distinct
players. Invisible/bonded players remain hardware members. Room names and roles
are shown separately from model/manufacturer observations. Related players appear
only when both current endpoints independently report compatible membership and
coordinator information. A changed UDN invalidates the earlier mapping.

Topology `Location` URLs are not followed; vanished entries are ignored. No IP,
model, MAC, manufacturer or presence is manufactured from a neighbouring player's
report. Group identifiers never merge Sonos devices, VSSL zones or Cast receivers.

## Bounds and limitations

- Selected Wi-Fi, eligible scope, current presence and endpoint-object checks apply
  at admission and result application. Cached/native advertisements cannot trigger
  VSSL status queries. Only current description responses trigger Sonos queries.
- At most 32 audio endpoints per scan, one lookup each, shared bounded metadata
  workers, two-second lookup deadline, tracked sockets and explicit lookup status.
  Transient transport failures can receive one fresh-connection retry within the
  original window. Typed Android refusal/routing errors are classified before
  retry and are not retried.
  Normal scan draining lets admitted descriptions finish their dependent metadata
  queries; Stop/deadline cancellation still closes every lane and socket.
- VSSL reads one bounded frame. Sonos HTTP/XML bodies are capped at 64 KiB with
  depth/node/attribute bounds, DTD/entity rejection, duplicate identity rejection
  and at most 64 unique players. No redirects or arbitrary advertised control URLs.
- Mapping and names disappear with the scan/session. Clipboard export happens only
  when the user chooses Copy device details. Hashes are internal correlation tokens,
  not a claim of anonymization; no mapping identifier is exported.
- AirPlay-disabled VSSL devices without a qualifying advertisement, other model
  generations/protocol variants, failed Sonos topology queries and incompatible
  snapshots remain unmapped. A failed lookup does not mean a device is absent.
- Physical-device compatibility is still unverified for this change. Synthetic
  fixtures cannot prove the user's firmware returns the reviewed protocol shape.

## Protocol references

Original Kotlin implementation; no upstream library, source code or database is
bundled. Public implementation references establish wire constants/field semantics:

- [VSSL A.6x manufacturer specifications](https://www.vssl.com/vssl-products/a6x).
- [vsslctrl discovery](https://github.com/vsslctrl/vsslctrl/blob/644586fa183def1f23f0bfd239454fa8422e3dbe/vsslctrl/discovery.py),
  [status framing](https://github.com/vsslctrl/vsslctrl/blob/644586fa183def1f23f0bfd239454fa8422e3dbe/vsslctrl/api_alpha.py),
  [model/zone identifiers](https://github.com/vsslctrl/vsslctrl/blob/644586fa183def1f23f0bfd239454fa8422e3dbe/vsslctrl/data_structure.py).
- [SoCo local topology parser](https://github.com/SoCo/SoCo/blob/master/soco/zonegroupstate.py)
  and [SOAP service paths](https://github.com/SoCo/SoCo/blob/master/soco/services.py), inspected 2026-11-16.
- [Sonos players and groups](https://docs.sonos.com/docs/how-sonos-works).

The VSSL REST API documentation describes an external VSSL Agent. Network Lens
neither requires that agent nor treats its endpoint as an amplifier endpoint.

## Regression coverage

`AudioZonesTest` exercises model/zone validation, strict protocol parsing, private
field exclusion, group/coordinator conflicts, bonded players, absent members,
fixed read-only requests and bounded loopback exchanges. The emulator-only
`audio-zones` mode covers current-response admission, in-memory copies, late replies,
Stop, hardware/category presentation, related-peer filtering, details and copy
in both themes at normal/200% text. See BUILDING.md for the direct commands.

## HEOS

A current UPnP root device type `urn:schemas-denon-com:device:ACT-Denon:1`
qualifies the same peer for two fixed TCP 1255 commands: `player/get_players`
and `group/get_groups`. The unique roster entry whose numeric IP equals the
responding peer supplies its own player name/model. A missing/ambiguous IP is
unsupported; a neighbouring player's model or manufacturer is never borrowed.
Groups require matching player/group IDs, one valid leader and reciprocal reports
from independently current peers. A group response failure preserves the direct
player model and reports partial metadata. No control, account or subscription
commands are sent.

## MusicCast

A current Yamaha/Yamaha Corporation UPnP MediaRenderer description qualifies
fixed HTTP port 80 requests under `/YamahaExtendedControl/v1/`:
`system/getDeviceInfo`, `system/getFeatures`, and `dist/getDistributionInfo`.
The first supplies the responding hardware model and reported product category;
features supplies the actual `main`/`zone2`/`zone3`/`zone4` hardware zones.
Receiver categories take precedence over generic playback capabilities. Soundbar
and subwoofer reports use the speaker icon/category with the precise reported
category retained as source-labelled evidence.

Distribution is distinct from hardware zones; the reported distribution zone
(or the documented `main` default) is labelled separately. A client appears related only when
one current server for the group explicitly lists it and the client's own current
report agrees. Conflicting servers suppress associations. No distribution setting,
power, playback or volume is changed. Older Yamaha devices without these APIs show
a lookup failure rather than an inferred MusicCast model.

## BluOS

Current `_musc._tcp.` and `_musp._tcp.` advertisements qualify **their own SRV
port** for a fixed `GET /SyncStatus`. A response must identify the same numeric IP
and queried port. Player model, brand and name remain source-labelled; player IDs
and advertised service names are not hardware grouping links or interface MACs.
The NAD CI580-style multi-port case retains separate players on the same IP
(e.g. ports 11000, 11010, 11020, 11030). Unadvertised ports are never probed.

Primary/secondary associations require reciprocal current reports. A roster
cannot create devices, supply a neighbour's identity, or trigger queries to its
addresses. A fixed-group name is displayed as a report, not used as a shared
hardware key. Ambiguous hierarchies preserve the responding player's identity
while withholding group mapping.

## AirPlay and Cast groups

Current AirPlay `gid`/`pgid` UUID advertisements produce separately labelled group
and parent-group observations. `igl` supplies a reported role, and `gpn` supplies
an optional group name. Matching current advertisements are described as **other
devices advertising this group**, not authenticated membership. Conflicting
coordinators suppress associations. Pairing/household identifiers do not establish
groups or hardware identity. Unknown identifier formats remain unmapped. No
pairing, authentication or audio transport is attempted.

Explicit virtual Cast advertisements remain separate from receiver identity. A
current physical receiver advertisement additionally qualifies the same peer for
`GET /setup/eureka_info?params=multizone` on port 8008. Static/dynamic target names
and explicit self-hosting ports are descriptive only. These HTTP lists are not a
speaker membership roster; their entries never establish physical membership,
stereo pairs or home-theater roles. Advertised leader addresses are not followed.
HTTPS-only metadata and authenticated Cast membership protocols remain unsupported.

## Additional bounds and verification

These readers share bounded metadata workers and a two-second whole-lookup deadline,
including optional requests. The added budget is 64 lookups per scan, at most eight
per endpoint, one attempt per protocol/port, plus 64 admitted group advertisements.
Each endpoint retains at most 16 player reports. JSON is bounded to 32 KiB, XML to
64 KiB; roster sizes, depth and strings are bounded. Fixed requests use sockets
bound to the selected Wi-Fi. Stop, stale/replaced endpoints and ineligible scope
reject late results. Optional failures remain visible as partial metadata.

`BroadAudioTest` covers parser shape/type ambiguity, self selection, multi-port
players, reciprocal/conflicting groups, private-field exclusion, read-only request
allowlists, loopback framing/errors, deadlines and cancellation. The emulator-only
`audio-zones` mode additionally exercises cached/current admission, limits, copied
state, scoped snapshots, late replies and Stop for these readers. Synthetic tests
do not establish compatibility with real firmware; household hardware has not
been tested with this change. Generic media services still cannot identify a
physical amplifier, television, thermostat or software player by themselves.

Primary protocol references inspected for this implementation:

- [Denon HEOS CLI specification](https://assets.denon.com/documentmaster/us/heos_cli_protocolspecification-version_04062020.pdf)
  and [PyHEOS player fields](https://github.com/andrewsayre/pyheos/blob/dev/pyheos/player.py).
- Yamaha Extended Control vendor specifications:
  [Basic v2.0](https://github.com/rsc-dev/pyamaha/blob/master/doc/YXC_API_Spec_Basic_v2.0.pdf),
  [Advanced v2.0](https://github.com/rsc-dev/pyamaha/blob/master/doc/YXC_API_Spec_Advanced_v2.0.pdf).
- [BluOS Custom Integration API 1.7](https://bluesoundprofessional.com/wp-content/uploads/2025/06/BluOS-Custom-Integration-API_v1.7.pdf).
- [pyatv AirPlay discovery fields](https://github.com/postlund/pyatv/blob/master/docs/documentation/protocols.md).
- [PyChromecast HTTP group descriptions](https://github.com/home-assistant-libs/pychromecast/blob/master/pychromecast/dial.py)
  and [distinct Cast membership protocol](https://github.com/home-assistant-libs/pychromecast/blob/master/pychromecast/controllers/multizone.py).
