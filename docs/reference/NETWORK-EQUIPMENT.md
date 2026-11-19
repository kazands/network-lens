# Public equipment identity

Original GPL-3.0-only parsers; no Fing/Nmap code, databases, controller
API, authentication, ARP cache, cloud lookup or saved inventory is used.

## Ubiquiti

The selected Wi-Fi socket sends the four-byte V1 (1,0,0,0) and V2 (2,8,0,0)
public discovery requests to the attached IPv4 broadcast address, UDP 10001.
Two waves occur seven seconds apart within a 12-second receive window and the
shared scan deadline. Custom narrower ranges skip this broadcast method rather
than contacting devices outside the requested range.

Supported replies have a V1 command 0 or V2 command 6/9 header, exact declared
length and bounded type/length/value fields. An interface MAC/IP tuple must match
the actual sender. Header-only echoes, unknown versions, conflicting duplicate
fields, invalid UTF-8, malformed lengths and wrong source ports are rejected.
Reported product name, model or board, name, firmware and matching interface MAC
remain source-labelled. No physical category is inferred from an ambiguous board
code or from the Ubiquiti family alone. Advertised endpoints are never followed.

## MikroTik

A receive-only selected-Wi-Fi socket listens on UDP 5678 for up to 12 seconds.
It does not send control packets or MNDP advertisements. Supported zero-header
MNDP announcements need a valid MAC, literal MikroTik platform and board string.
When an IPv4 address is supplied it must match the sender. An announcement can
arrive outside our window; an absent response does not establish absence.

Both methods accept only current in-scope IPv4 senders, at most 512 packets and
128 distinct identity reports each. Differing reports remain visible to conflict
handling; repeated identical reports are coalesced. Stop/network loss closes owned
sockets. An occupied discovery port is reported, never stolen with port reuse.
IPv6 equipment discovery and Ubiquiti multicast-only/legacy V0 responders remain
unsupported by these new readers.

## Protocol research provenance

No source from these references is bundled or translated. They document the wire
facts used in the independently written bounded parsers:

- [unifi-discovery](https://github.com/uilibs/unifi-discovery/blob/main/unifi_discovery/__init__.py),
  examined 2026-11-18, Git blob `bdad0fd8e88ce7e1b25a10ab1173556e4b2ec6d1`:
  V1/V2 headers, TLV identifiers and IP/MAC tuples. Apache-2.0; J. Nick Koston.
  Its cache, external routing targets, ARP enrichment and TLS-bypassing HTTP probes
  are deliberately not incorporated.
- [Wireshark MNDP dissector](https://github.com/wireshark/wireshark/blob/master/epan/dissectors/packet-mndp.c),
  examined 2026-11-18, Git blob `3351190561ec3a3df9c836f39b486882b30e2326`:
  UDP port and TLV field numbers/lengths. GPL-2.0-or-later; Joerg Mayer and
  Wireshark contributors. No dissector code is incorporated.
- [MikroTik neighbor discovery](https://help.mikrotik.com/docs/spaces/ROS/pages/24805517/Neighbor%20discovery):
  broadcast-domain scope, configurable announcements, platform/board fields.

Synthetic parser, truncation, contradictory-field, sender-correlation, timing,
scope and cancellation checks live in `NetworkEquipmentTest`. Physical equipment
and Android broadcast delivery must be verified separately; synthetic success is
not a claim of compatibility with every firmware.
