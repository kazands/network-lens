# Broader local recognition

Included in Network Lens 0.3.22. See [release verification](VERIFICATION-0.3.22.md).
Recognition still uses current responses on the selected Wi-Fi. Results remain
in memory, and no scan data or household identifiers are sent to a recognition
service. Missing models remain unknown.

## Catalogue and scheduling

The pinned Home Assistant Core projection contains 348 rules: 179 mDNS rules
covering 122 service types, 75 HomeKit model patterns, and 94 SSDP rules across
44 integration domains. Every condition within a rule is required; an OR between
rules does not become an OR between their fields. Case and wildcard matching are
preserved. SSDP headers and description fields are joined only for the same
responding address and description URL, never across unrelated services.

These are integration candidates, not a universal retail-model database. Reviewed
families can supply a fallback label when no useful name/model exists. HomeKit
model-pattern matches are shown as integration candidates only: patterns such as
`Socket` cannot prove a manufacturer. Samsung AirPlay alone means a Samsung media
device; it does not establish television hardware. Existing model, HomeKit
category and protocol-specific hardware evidence still govern icons. An audio
capability cannot by itself override known thermostat or bridge hardware.

The old 48-type admission ceiling is removed. Native Android browsing has six
concurrent slots, each with a three-second browse window. Observed types have a
512-type safety bound; hitting it is reported as Partial. Direct mDNS queries
cover the initial types, observed types and the complete bundled HA type list in
batches of at most eight, with unqueried types ahead of retries. Recovery and
reverse-name queries retain slots without monopolizing every batch. The scan's
55-second deadline, packet/byte bounds, cancellation and separate resolution
limits remain. Catalogue size is separate from concurrent Android work. A busy
network can still exhaust bounded work; that is reported, not called complete.
Retained reports now allow all 512 admitted types plus 128 other method entries.
Unexpected report overflow is explicitly counted, including across JSON round
trips; any aggregate failure explanation remains visible when entries are omitted.

The import is reproducible offline:

```sh
python3 scripts/generate-discovery-matchers.py --check
```

Sources and exact Git blob/SHA-256 pins are in `android/ha-original/` and
`android/ha-matcher-provenance.json`. Source is Home Assistant Core's
[generated discovery indexes](https://github.com/home-assistant/core/tree/dev/homeassistant/generated),
under Apache-2.0. No HA DHCP, pairing, control or cloud code is imported.

## Matter registry

Commissionable `_matterc._udp` records can supply decimal vendor/product IDs in
`VP`, a device-type ID in `DT`, and a device name in `DN`. DN is not a model.
Extended discovery can exist outside an open commissioning window; operational
Matter advertisements alone do not guarantee that these identity fields exist.

The bundled 2026-11-17 public DCL snapshot contains 5,479 usable vendor/product
records; 121 invalid, test-vendor or incomplete records were excluded. It maps
an exact vendor/product pair to the registered vendor, product name, part number
and device type. Unknown pairs are not guessed. Contradictory pairs, reported
models or manufacturers suppress an exact catalogue title. Registry labels do
not fill the reported Manufacturer/Model fields, and evidence received before
the background catalogue load resolves when the catalogue becomes available.
Grouped endpoints also compare their reported vendor/product IDs directly: an
unknown conflicting pair suppresses an exact Matter title even when that pair
has no registry entry. Matching IDs or a peer with no Matter identity do not
prevent otherwise supported recognition.

This is an offline projection of public registry facts, not a live cloud lookup,
certification verification, or guarantee of firmware compatibility. It includes
no firmware URLs or downloads. The endpoint supplied no block-height response
header for this snapshot; provenance records that limitation rather than claiming
an atomic block-height snapshot. Page hashes and the retrieval time are retained.

```sh
python3 scripts/update-matter-models.py --refresh  # intentional maintenance only
python3 scripts/update-matter-models.py --check    # normal offline build/test
```

Primary references: [CSA main-net endpoints](https://github.com/zigbee-alliance/distributed-compliance-ledger/blob/master/deployment/persistent_chains/main-net/main-net-csa-endpoints.md),
[DCL model module](https://github.com/zigbee-alliance/distributed-compliance-ledger/tree/master/x/model),
and [public registry](https://on.dcl.csa-iot.org/dcl/model/models).
The retained source projection and hashes are in `android/matter-original/` and
`android/matter-model-provenance.json`. No exclusive rights are claimed over these
public facts; the ledger software's license is not asserted as a license to every
vendor's supplied data.

## Additional protocol paths

| Path | Requests and interpretation | Limitations |
|---|---|---|
| Amazon Fire TV | Explicit WhisperPlay mDNS queries, TXT friendly names and one reviewed public Fire TV Stick 4K type code; SSDP includes bounded DIAL searches | Unknown codes retain a service-family label. Generation is unspecified; raw missing fields remain unknown. See [Fire TV recognition](FIRE-TV-RECOGNITION.md). |
| TP-Link Easy Smart | UDP 29809 → 29808, discovery opcode 0 only; validate reply opcode, client correlation, sequence, length, MAC and sender IP; retain public model/name/version fields | Legacy discovery envelope only. No GET, login, session negotiation, credentials, port configuration or SET. Physical switch compatibility still requires testing. |
| Samsung TV | Fixed `GET :8001/api/v2/` after an observed TCP/service candidate; require the Samsung SmartTV identity marker | Newer compatible TV firmware only; older TVs still rely on SSDP/UPnP. No pairing or remote-control WebSocket. |
| WiZ | UDP 38899 `getSystemConfig` | Module code is electronics evidence, not a retail model or automatic light category. No registration request. |
| Yeelight | Multicast UDP 1982 `M-SEARCH` for `wifi_bulb`; validate advertised endpoint against sender | LAN control must be available. `color`, `mono`, etc. remain model-family evidence. No control connection. |
| Govee | Multicast UDP 4001 `scan`, receive on 4002; require sender IP and reported SKU | Supported LAN-enabled devices only. No control requests on 4003. |
| LIFX | UDP 56700 GetService, then same-peer GetVersion on the reported service port; correlate transaction and target | Retain numeric vendor/product IDs. No copied product registry or guessed retail model; switches are not automatically lights. |
| Tuya | Receive only on UDP 6666/6667/7000; validate 55AA CRC/ECB and 6699 GCM announcement envelopes and sender IP | Identifies a Tuya-compatible platform, not the white-label brand or retail model. No local keys, account lookup, active device queries or DPS control. Device IDs are not retained. |

The added UDP windows are six seconds, with packet/device/variant limits and
selected-network binding. WiZ/Yeelight/Govee/LIFX active broadcasts require a full
IPv4 subnet scan; narrowed scans explicitly skip those paths. Easy Smart can
query only the selected IPv4 hosts. Tuya receive-only admission respects the
selected range. Busy ports and interrupted/limited work appear in scan status.
UDP discovery ports are not represented as open TCP ports.
Samsung and Govee protocol-qualified model titles do not fill an absent reported
manufacturer field.
Queued EAP identity checks receive a terminal outcome on Stop or deadline cleanup,
even when the worker never starts. Completed outcomes are preserved.

Wire references (protocol facts, not bundled control clients):

- [SamsungTVWS public REST identity](https://github.com/xchwarze/samsung-tv-ws-api/blob/master/samsungtvws/rest.py).
- [PyWiZLight protocol methods](https://github.com/sbidy/pywizlight).
- [Yeelight inter-operation specification](https://www.yeelight.com/download/Yeelight_Inter-Operation_Spec.pdf).
- [Govee LAN guide](https://app-h5.govee.com/user-manual/wlan-guide).
- [LIFX information messages](https://lan.developer.lifx.com/docs/information-messages).
- [TinyTuya UDP envelope reference](https://github.com/jasonacox/tinytuya/blob/master/tinytuya/core/udp_helper.py).
- [Easy Smart protocol implementation](https://github.com/rgl/ansible-collection-tp-link-easy-smart-switch/blob/master/plugins/module_utils/protocol.py)
  and [independent discovery framing research](https://www.chrisdcmoore.co.uk/post/tplink-easy-smart-switch-vulnerabilities/).

The Easy Smart stream table/transform adaptation retains the GPL-3.0 source and
license under `android/easysmart-original/`, with a modification notice and
`android/easysmart-provenance.json`. TinyTuya's MIT reference notice is bundled.

## Explicit remaining gaps

Roku SSDP remains available, but no mobile ECP client is added: the current
[official ECP documentation](https://developer.roku.com/dev/docs/external-control-api)
restricts commands from third-party mobile applications. Xiaomi miIO and Roomba
active queries are not included in this change: their mere port numbers do not
establish a credential-free retail identity method. Existing Xiaomi mDNS matcher
candidates are retained. Lutron Caseta recognition describes its network bridge;
individual Clear Connect switches are not independent IP endpoints.

None of the new methods guarantees identification of sleeping devices, devices
that suppress discovery, inaccessible VLANs, or credential-gated model data.
Synthetic regression coverage and emulator checks do not substitute for physical
Wi-Fi compatibility tests. The release/version and published APK are unchanged.

## Verification

The initial broader-discovery verification passed all 39 host suites, including 22 matcher/scheduling,
31 Matter, 248 LAN identity, 55 LIFX and 1,111 Easy Smart checks. The development
APK and instrumentation companion build successfully; the direct APK permission
and security audits pass. Both generated recognition datasets pass their offline
reproducibility checks.

- Android API 37: **973 checks passed** across device-identities (655), local-metadata (49), scan-efficiency (22), service-retries (211), resolver-upgrade (21), discovery-upgrade (15).
- Android API 26: **879 checks passed** across device-identities (655), service-retries (208), discovery-upgrade (16).

The emulator runners verified the installed application and test APK hashes.
The checks use synthetic identities, including conflicting observations, fresh
scan isolation, unknown reported manufacturer fields, self-only range guards,
more than 48 service types, real Android browse scheduling, and scalable list/detail
layouts. Working-tree whitespace and prohibited-file/credential checks also pass.
No physical phone was operated for this verification, and no release was published.
An independent pre-publication review and physical Wi-Fi compatibility testing
remain separate steps.

### Matter grouping follow-up (2026-11-17)

The raw-ID compatibility regression failed against the earlier code and passed
after the fix. All 39 host suites passed again, including **53 Matter checks**.
The development APK, permission/security audits and instrumentation build passed.
A focused independent Deep review approved the code and regression tests.
Android API 26 passed the full `device-identities` suite: **695 checks**, including
the new grouped known/unknown vendor/product conflicts, both input orders,
snapshot retention and matching/absent peer identity cases. The runner verified
the installed application and test APK hashes.

On Android API 37, the new Matter grouping assertions passed, but the enclosing
`device-identities` suite failed later while reading copied thermostat details:
Android denied clipboard access because the application lacked focus. The failure
repeated; a bounded wait in the test did not resolve it and was removed. This run
is **not** a passing full Android 17 suite. Its clipboard/focus failure remains
unresolved and must be checked before publication. No clipboard implementation
or existing clipboard test was changed by this follow-up.
No physical device was operated, and the update was not published.

Subsequent [pre-PR verification](PRE-PR-VERIFICATION.md) records passing Android 17
clipboard/identity checks after a fresh emulator boot, broader matrix results,
test corrections, and the remaining permission-dialog and physical-test limits.

The candidate verification history above predates release versioning. See
[current release verification](VERIFICATION-0.3.22.md) for the final artifact and checks.
