# Recognition coverage expansion

Included in 0.3.26. This reference records coverage contracts and remaining limits.
No new permission, persistent inventory, cloud identification or background scan.
See [exact verification results and remaining physical checks](../archive/VERIFICATION-0.3.26.md).

## Coverage contracts

`android/recognition-coverage.json` records an explicit decision for every one of
the 174 bundled Home Assistant integration domains. The offline check rejects
new/stale domains and mismatches with runtime family labels. Its generated
[coverage inventory](RECOGNITION-COVERAGE.md) lists all 174 integration families. A family label is not a claim of exact
product recognition. Existing model readers operate independently. HomeKit
model-only candidates remain technical evidence and never establish a brand.

Family labels are fallbacks for unnamed devices; useful reported names remain.
Details explain recognised families even when a useful name exists. Family-only
matches do not invent a reported manufacturer, model or exact generation. Broad
ecosystems do not imply a physical type; existing reviewed bridge-role matchers
remain explicit exceptions. Generic software/media/printing services do not classify their host.
Nanoleaf's explicit lighting API and WLED's lighting service supply a lighting
role, separate from exact model recognition. Conflicting roles remain unresolved.
The large naming catalogue and its separate 651-entry Android hardware table
are unchanged; adding arbitrary category guesses to that table would reduce trust.

## Recovery and address fairness

- SSDP has two waves seven seconds apart in a 12-second window. Each wave queries
  ssdp:all, rootdevice, DIAL, Sonos ZonePlayer and Belkin controllee. No app launch
  or control request is sent. IPv4 and interface-scoped IPv6 ff02::c use independent
  bounded sockets and the same guarded description reader. Each exchange has a
  4,096-raw-packet ceiling, 512 admitted senders, at most 128 parsed packets and
  64 retained records per sender, and 400 retained records overall. Duplicates
  and rejected traffic do not spend the retained-record budget. Per-peer limits
  do not terminate the other senders' window; reached limits remain visible.
- A late mDNS recovery request targets up to 32 service types from current
  responding endpoints without a specific model. It adds at most one opportunity
  after the existing two resolution retries, independently for each IP family.
  Queued retries and the 1.2-second reply window after the final opportunity
  keep discovery pending on each active receiving family. Failed or absent
  receive channels do not extend the scan; remaining work at the shared deadline
  is reported as unfinished. Cached native callbacks cannot supply identity.
- Tuya's passive window is 12 seconds. There is no guarantee every device will
  announce within it; no assumed five-second announcement interval is used.
- IPv6 echo admission permits 64 addresses, at most eight per advertised service
  instance or current routed host. Address deduplication is global. The queue is
  bounded and round-robin; advertised grouping keys affect scheduling only, never
  merge identity. Different services on one physical device can still have separate
  scheduling owners; these are not hardware identifiers. TCP confirmation fallback and explicit limit reporting remain.
- The total scan deadline remains 55 seconds. Discovery windows finish before
  admission closes when the shared deadline allows it. Existing metadata requests
  retain their size, time, retry and scope limits.

## Additional identity readers

[Ubiquiti and MikroTik discovery](NETWORK-EQUIPMENT.md) adds bounded public identity
for network equipment outside the existing TP-Link methods.

The eSCL reader follows only current `_uscan._tcp` / `_uscans._tcp` advertisements
with a safe `rs` root and advertised port. It issues one bounded GET of
`ScannerCapabilities` through the existing metadata queue and transport. Secure
advertisements require validated TLS; there is no downgrade. XML namespaces,
size, structure and scalar fields are validated; DTDs/entities and duplicate
identity fields are rejected. Only reported make/model and manufacturer are
retained as separate eSCL scanner details. They never populate the host's Model
or Manufacturer fields, product catalogue or hardware classification. Serial
numbers, admin links, icon links, jobs and scanned images are
not requested or retained. A scanner can be shared by another computer, so this
is service-reported identity, not proof of the physical host's identity or category.
The same separation applies before HTTP: Bonjour scanner names, models and
manufacturers remain service details labelled by type and port. Scanner TXT
IDs, MAC addresses and software fields cannot establish or conflict with host
identity. Both secure and cleartext scanner advertisements retain the existing
metadata admission and transport checks.

Protocol references: [OpenPrinting eSCL capabilities](https://github.com/OpenPrinting/go-mfp/blob/master/proto/escl/scannercapabilities.go),
[Nanoleaf lighting discovery](https://support.nanoleaf.me/hc/en-us/articles/41105798500628-API-Quick-Start-Guide),
and the pinned Home Assistant WLED service rule. No source implementation was copied.

## Measuring remaining gaps

The original 14 product-recognition benchmark cases remain. The new independent
cross-family fixtures cover 20 mDNS families with case/format variants, misleading
fields, hardware-versus-service negatives, eSCL metadata, recovery and IPv6 limits.
Equipment fixtures add malformed packets, conflicts, unsupported protocols, sender
mismatch and lifecycle boundaries. Android checks exercise real Device snapshots,
grouping, current-service admission, Stop, XML and presentation. Test assertions
are not a count of supported retail models or a household accuracy percentage.

Physical verification must record presence, family, physical type, exact model,
wrong identification and request outcome separately, using the existing redacted
baseline tool. Include awake/sleeping devices, private addresses, dual-stack,
regional model variants and firmware differences. Never commit household captures.

## Explicit remaining boundaries

- Apple/private-address devices that return no identity cannot be assigned an exact
  model from a name, open port or private MAC.
- NAS/camera administration APIs, ONVIF identity and some smart-home APIs require
  credentials or pairing. This change adds no authentication attempts, generic web
  crawling, RTSP control, guessed default passwords or TLS bypass.
- LIFX numeric IDs remain visible. Its public registry's additional reuse terms need
  resolution before bundling a snapshot; public availability alone is not a license.
- Roku ECP is not added: current vendor documentation restricts commands from
  third-party mobile platforms. Existing SSDP descriptions remain available.
- Xiaomi handshake discovery alone does not report device type. Paired Matter,
  Tuya retail names and hub-attached Zigbee/Z-Wave children may need information
  unavailable to an unauthenticated IP scanner.
- Exact product interpretations beyond family labels still need targeted protocol/model
  evidence and fixtures; the inventory records this boundary for every integration.
- Cross-VLAN discovery, IPv6 equivalents for every vendor protocol, and physical
  performance/firmware coverage are not claimed complete.

Sources: [miIO discovery limits](https://python-miio.readthedocs.io/en/stable/discovery.html),
[Roku ECP](https://developer.roku.com/dev/docs/external-control-api),
[LIFX registry terms](https://github.com/LIFX/products/blob/master/README.md).
