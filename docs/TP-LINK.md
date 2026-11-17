# TP-Link plug and switch discovery

The source implementation adds local, read-only discovery for supported Kasa and
Tapo plugs and wall switches. It runs only during a user-started foreground scan,
using the selected Wi-Fi. It does not authenticate, pair, switch a relay, change a
setting or contact TP-Link's cloud. Available starting with Network Lens 0.3.18.

## Discovery and admission

- UDP 9999 sends only `system.get_sysinfo`, using the legacy XOR framing.
- UDP 20002 sends a discovery request with a fresh transaction ID. Replies must
  have the supported header, matching transaction, exact payload length and CRC32.
  CRC32 detects framing damage; it does not authenticate a LAN responder.
- Both formats require a successful numeric response code, a supported plug/switch
  family and a bounded model string. Strict JSON rejects duplicate keys, invalid
  UTF-8, excessive nesting and object/array path impersonation.
- Presence comes from the current UDP responder. Supplied IP addresses, cloud
  identifiers, owner hashes, credentials and child-device rosters are not retained
  or followed. Only model, alias, firmware, hardware version, family and explicitly
  reported MAC are extracted. A missing manufacturer remains unreported; protocol
  evidence qualifies the local TP-Link catalogue without filling that raw field.
- Discovery uses a private ephemeral socket bound to the selected Wi-Fi/address.
  Whole-subnet scans use that subnet's directed broadcast; narrowed scans query
  their selected IPv4 hosts directly. IPv6-only scans skip this IPv4 protocol.
- Two rounds, four seconds maximum, up to 1,024 targets, 512 received packets and
  128 responding devices bound the work. Deadline, cancellation, network changes
  and resource cleanup follow the scan lifecycle. Limits/interruption are reported
  as partial discovery. No response is not proof that no TP-Link devices exist.
- Duplicate replies on the same transport are ignored. The two protocols enrich
  one endpoint; incompatible model reports remain conflicts. Discovery UDP ports
  are not presented as observed TCP listeners.

## Product display

Exact reviewed models qualify `TP-Link Kasa …` or `TP-Link Tapo …` titles. Regional
suffixes remain in the raw model observation and share a canonical product title.
HomeKit outlet/switch categories agree with these models; audio services cannot
replace their physical category.

| Models | Display type / icon |
| --- | --- |
| HS100, HS103, HS105, HS110, KP100, KP105, KP115, KP125, EP10, EP25 | Smart plug / plug |
| HS200, HS210, KS200, KS200M, KS205 | Light switch / switch |
| HS220, KS220, KS220M, KS225, KS230 | Dimmer switch / switch |
| P100, P100M, P105, P110, P110M, P115, P125, P125M | Smart plug / plug |
| S500, S505 | Light switch / switch |
| S500D, S505D | Dimmer switch / switch |

This is a reviewed category table, not a claim that every hardware/firmware version
has been tested. Unknown models retain reported information without a guessed
retail identity or plug icon. The shared `IOT.SMARTPLUGSWITCH` family alone cannot
distinguish a plug from a dimmer. Model prefixes are not classification rules.

## Limits

Authenticated metadata, newer encrypted discovery extensions, UDP 20004, bulbs,
cameras, hubs, power-strip child outlets and hub-connected accessories are outside
this change. Devices requiring those paths may still appear through other existing
scan methods. No account credentials or persistent device history are introduced.

## Sources and provenance

Original Kotlin implementation; no python-kasa code or fixture database is copied.
The protocol behavior was researched using the maintained project's documentation
and source. Its library is GPL-3.0; no new library dependency is introduced.

- [python-kasa discovery documentation](https://python-kasa.readthedocs.io/en/stable/topics.html)
- [python-kasa discovery implementation](https://github.com/python-kasa/python-kasa/blob/master/kasa/discover.py)
- [TP-Link HS103 and related plug comparison](https://www.tp-link.com/us/home-networking/smart-plug/hs103/)
- [TP-Link plug setup guide and model families](https://static.tp-link.com/upload/manual/2024/202407/20240718/1910013750_EP25%282-Pack%29%28US%29_UG_V1.pdf)
- [TP-Link switch and dimmer catalogue](https://www.tp-link.com/us/home-networking/smart-switch/?page=1)
- [Tapo plug catalogue](https://www.tapo.com/en/product/smart-plug/)
- [Tapo S500](https://www.tapo.com/us/product/smart-switch/tapo-s500/)
- [Tapo S505](https://www.tapo.com/us/product/smart-switch/tapo-s505/)
- [Tapo S500D](https://www.tapo.com/us/product/smart-switch/tapo-s500d/)
- [Tapo S505D](https://www.tapo.com/us/product/smart-switch/tapo-s505d/)

## Verification

`TpLinkDiscoveryTest` uses synthetic packets and a deterministic clock/channel to
exercise parsing, admission, conflicts, target scope, retry/cancellation/deadline
behavior and safety limits. `TpLinkChecks`, in the emulator-only device-identities
suite, exercises current-response admission, presentation, icons, raw-field
preservation, grouping, Stop and fresh-scan isolation. The existing identity layout
matrix includes a plug and dimmer across themes and enlarged text.

Physical compatibility is separate from these fixtures. Do not commit household
responses, names, identifiers or screenshots as test data.

The production host discovery/parser was also checked against an authorized
physical HS103: both UDP formats produced the canonical HS103 title and smart-plug
type, including the modern transaction/CRC checks. This was a desktop protocol
check, not an on-phone scan or a compatibility test of every listed model. Physical
dimmer compatibility still requires validation. No household capture is included.

Validation of this changeset: all 33 host suites passed, including 797 TP-Link
checks; the direct development APK build and packaged security/permission audits
passed. The final API 37 emulator matrix passed 714 checks across device identities,
HomeKit, scan efficiency and session-only/cold-start behavior. An initial timing
fixture timed out; the final unchanged scan-efficiency fixture passed on rerun.
Independent Deep review approved after the self-only scan-status correction.
API 26 and a physical Android scan of this recognition change have not been run.
Exact release checks are in [0.3.18 verification](VERIFICATION-0.3.18.md).

Current release-wide checks are recorded in [0.3.19 verification](VERIFICATION-0.3.19.md).
