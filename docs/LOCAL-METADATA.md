# Local device metadata

Network Lens reads the following local service metadata. These are device-reported
observations, with source labels and per-endpoint lookup outcomes. Supported exact
manufacturer/model reports can feed the [offline product catalogue](DEVICE-IDENTITIES.md).
Every scan starts fresh and all metadata remains in memory.

[Amazon Fire TV recognition](FIRE-TV-RECOGNITION.md) additionally reads current
WhisperPlay friendly-name/type-code fields and permits the documented same-peer
`/upnp/dev/<UUID>/desc` description path. A reviewed type-code interpretation is
shown separately from reported Model; no login, pairing or control is performed.

| Service | Additional observations |
| --- | --- |
| Thread `_meshcop._udp` | `mn` model and `vn` reported vendor. Other Thread addresses and network identifiers are not interpreted as a hardware MAC. |
| ESPHome `_esphomelib._tcp` | Friendly name, firmware version and explicit MAC. Board and platform are labelled firmware-target details, not retail model/manufacturer. |
| Shelly `_shelly._tcp` | Read-only `/shelly` on the advertised port: model/type, firmware and explicit MAC. Gen1 `_http._tcp` advertisements with a Shelly instance/host form and exactly six or twelve hexadecimal ID digits also qualify. The ID is only a metadata-query marker, never a derived MAC. |
| WLED `_wled._tcp` | Advertised MAC plus `/json/info` for name, firmware, explicit MAC and platform. Firmware brand/product and Wi-Fi AP BSSID never supply hardware manufacturer/model/MAC. |
| Spotify Connect `_spotify-connect._tcp` | GET at the advertised `CPath` with the fixed `action=getInfo`: reported brand, model, name and device-type detail. Model is optional. No login, playback or account-changing action is generated. |
| IPP `_ipp._tcp` / `_ipps._tcp` | Get-Printer-Attributes at the advertised `rp` resource: `printer-make-and-model`. The value describes the print service, which can be a queue or virtual printer. It is not split into a guessed manufacturer. |

Only current wire service records can authorize these lookups. Cached Android
service callbacks remain candidates and cannot provide identity or authorize an
HTTP request. HTTP metadata additionally requires an already confirmed peer in
the selected Wi-Fi's direct eligible scope. Routed accessory advertisements do
not authorize HTTP. Sockets bind to the selected Android Network; scope and
network validity are checked before and after an exchange.

Requests go to the actual numeric peer and advertised port, never a hostname or
URL returned by the device. Spotify and IPP accept only bounded local paths,
without supplied queries, fragments, credentials or alternate authorities. There
are no redirects, guessed authentication credentials or secondary URL fetches.
IPPS uses platform TLS trust and endpoint verification, without a plaintext
fallback. Printers with untrusted certificates or required credentials can remain
unavailable. Successful metadata without an optional model leaves it unknown.

The existing 55-second scan-work deadline remains. HTTP metadata has two workers
per existing work lane, 32 queued tasks and at most 32 endpoint plans per protocol
and 96 overall. Each peer/protocol plan can retain two distinct validated
advertised targets, with at most one transient transport retry per target. All
attempts share one original two-second window within the remaining scan budget.
Changing advertised paths/ports cannot grow the plan beyond those bounds. Queued
metadata work rotates between endpoints, with at most eight waiting tasks per
endpoint in a lane. See [recognition planning](RECOGNITION-PLANNING.md). Each
exchange has a
400 ms connection timeout and reads bounded to at most 600 ms each. A socket
deadline also bounds TLS handshakes and writes. HTTP bodies are limited to 64 KiB;
JSON is limited to 32 KiB with depth/token/string limits. Only selected root JSON
fields or the correlated IPP printer-model attribute survive parsing.

Unavailable or invalid requested metadata is shown as a failed method; exhausted
queues/deadlines are incomplete methods. These states keep the scan Partial.
Stopping discovery closes sockets, shuts down the metadata lane/timer and rejects
late results. These additions do not establish a fix for the existing intermittent
HomeKit/HTTP Android resolution issue or measured coverage on real hardware.

## Local diagnostics

Each endpoint's details now include its admitted Shelly, WLED, Spotify Connect
IPP, Cast and Samsung lookup outcomes, labelled by protocol, IPv4/IPv6 and the attempted port
(with TLS for IPPS). Success without an optional model/manufacturer is explicit.
Connection refusal and routing failures require typed platform evidence; generic
connection failures remain generic. Read timeouts, HTTP errors, invalid metadata,
work limits, unfinished lookups and Stop remain distinct. A failed lookup does
not revoke independently confirmed presence or change Partial reporting.

The same panel identifies other current observations supplying that endpoint's
displayed model/manufacturer. It does not borrow a grouped address's details or
guess a brand from a MAC vendor. The worker may try the bounded same-peer targets
above; the panel itself does not initiate queries. Alternative
sources are evaluated from current observations, so a later local response can
appear alongside an earlier HTTP failure. Unknown values remain unknown.

These outcomes have six protocol slots per endpoint, live only in session memory and are
copied with progress/retained device snapshots. Stop/finalization seal pending
outcomes against late worker updates. A fresh scan has no inherited outcomes.
The explicit Copy device details action includes the source-labelled outcomes;
there is no automatic export, logging or persistence of endpoint diagnostics.

## Scan diagnostics

Scan method details distinguish connection failures/timeouts, TLS failures,
request-write failures, response-read failures/timeouts, non-200 HTTP status codes,
invalid/unsupported HTTP framing and unusable protocol metadata. A read or connect
timeout shortened by the remaining overall budget is reported as a metadata
deadline. Deadlines remain incomplete; other requested-metadata failures remain
failed. User cancellation does not become a connection or read failure. A valid
response omitting an optional model still leaves that field unknown.

Each failure category has a fixed aggregate counter. HTTP diagnostics retain only
the numeric status, without reason phrases, headers or bodies. Raw exception
messages, endpoint addresses, advertised paths and device identifiers are not
copied into diagnostic text. Counts stay in the current scan's RAM-only report.
The original ordinary-counter limit remains; the additional diagnostic vocabulary
is a finite enum. Event counts saturate at one million; received-byte counts
saturate at 32 MiB.

The direct mDNS receiver also counts datagrams per IPv4/IPv6 receive window,
ignored sources, oversized packets, non-response header packets, rejected response packets,
responses without live details, live-record responses and receive-budget stops.
A live-record response counter is not a count of confirmed devices. It grants no
additional presence or identity authority. No packet bytes or peer identifiers
are retained by the traffic counter.

The direct mDNS receiver separates each configured receive window into at most 4,096
raw datagrams, 8 MiB of copied payload and 1,024 full response decodes. Discarded
traffic consumes the raw packet/byte limits; query headers do not consume the
response-decoding limit. At most 256 small query packets are parsed for optional
question suppression. Exceeding a receive/byte/response limit remains Truncated;
skipping further query-suppression parsing does not discard response evidence.
IPv4 and IPv6 have separate budgets with aggregate counters; packets are not
deduplicated across windows or attributed to individual senders by these counts.

Current-scan traffic reduction has two bounded mechanisms:

- A recently observed identical QM question with an empty known-answer section
  can stand in for a scheduled question for one second. Only complete, bounded
  local questions qualify. Their records never supply presence or identity.
- Repeated service PTR queries include aged known answers only for complete,
  unexpired, same-sender services learned from current response packets in this
  scan. Answers below half their advertised TTL are omitted. A complete sequence
  occupies at most four 1,200-byte packets with the required TC framing; if it
  cannot fit, an ordinary query is sent instead. Cached Android callbacks and
  incomplete/proxy-only service data never authorize known-answer suppression.

Record/admission/queue limits, selected-Wi-Fi binding and the overall scan-work
deadline remain. Source-detail entries are capped at 1,024 characters so the
finite additional counters survive terminal reporting; source keys/counts remain
bounded. Historical measurements describe only their observed scans; they do
not establish complete device coverage or elimination of intermittent failures.

## Native and wire correlation

Modern Android service-resolution failures now record a fixed aggregate category
for their matching current-scan wire evidence: not observed, observed with no
retained graph, current incomplete graph, current complete same-sender graph, or
correlation unavailable. The first category means only that the bounded receiver
did not capture a matching PTR/SRV/TXT record; it does not prove stale Android
cache or an absent device. Queries and native callbacks do not supply wire evidence.
Earlier/withdrawn records and records dropped by retention limits can produce the
history-only category; it is not current presence or identity.

Failure-time snapshots are supplemented at native cleanup with counters for wire
evidence first observed after failure and graphs completed after failure.
Completion timestamps are retained for this diagnostic even when the underlying
identity records expire. A maximum of 256 wire-instance history entries and 256
native failures are kept in scan RAM. Invalid/unmatchable native identifiers and
exhausted correlation capacity remain explicitly unavailable. Only fixed labels
and counts enter the report; names, addresses and attributes are not exported by
this correlation. Records from different sender addresses never combine into a complete
graph. A fresh scan inherits no history; cleanup is idempotent and rejects late
writes. The existing Partial/failure policy, native APIs, retries, discovery
packets, admission limits and deadlines remain.

These diagnostics identify captured evidence and timing. They do not establish a
fix for the native-resolution issue or Spotify connection failures.

## Verification and known limits

Current release checks and exact artifact identities are in
[release verification](VERIFICATION-0.3.15.md). Earlier synthetic transport,
TLS, mDNS, Android and authorized physical diagnostics are retained in the
[metadata verification history](archive/METADATA-VERIFICATION.md).

Advertised Spotify endpoints can time out or refuse connections. Intermittent
native HomeKit/HTTP address resolution remains unresolved. Longer timeouts,
repeated attempts and alternate resolver experiments have not established a
production recovery fix. These failures stay explicit and keep scans Partial.
Broader real-device compatibility, trusted IPPS on physical hardware and complete
Thread-mesh coverage remain unverified. See [development status](STATUS.md).

## Protocol references

These local metadata readers independently implement documented wire behavior.
No vendor implementation or additional runtime dependency is incorporated.

- [Multicast DNS traffic reduction](https://www.rfc-editor.org/rfc/rfc6762.html#section-7), known-answer and duplicate-question suppression.
- [Thread Group border-router white paper](https://www.threadgroup.org/Portals/0/documents/support/ThreadBorderRouterWhitePaper_07192022_4001_1.pdf), section 9.
- [ESPHome mDNS implementation](https://github.com/esphome/esphome/blob/5f5a81434df4d47c3b9fb8d3927f11791a5bcbf6/esphome/components/mdns/mdns_component.cpp), TXT field definitions.
- [Shelly mDNS](https://shelly-api-docs.shelly.cloud/gen2/General/mDNS/), [Gen1 compatibility](https://shelly-api-docs.shelly.cloud/gen2/General/gen1Compatibility/) and [Gen1 `/shelly`](https://shelly-api-docs.shelly.cloud/gen1/#shelly).
- [WLED JSON API](https://kno.wled.ge/interfaces/json-api/) and [WLED mDNS advertisement](https://github.com/wled/WLED/blob/fce41f2655c3087d3eaf95c028d2856967cbc86e/wled00/wled.cpp).
- [Spotify ZeroConf API](https://developer.spotify.com/documentation/commercial-hardware/implementation/guides/zeroconf), Get Information.
- [RFC 8011](https://www.rfc-editor.org/rfc/rfc8011.html), sections 4.2.5 and 5.4.9; [PWG IPP guide](https://www.pwg.org/ipp/ippguide.html), printer discovery and resource paths.
