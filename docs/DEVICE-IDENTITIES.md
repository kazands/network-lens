# Canonical device identities (unreleased)

The list uses a recognized product as its primary title, followed by its category
and a useful custom network name. Raw network names, reported models,
manufacturer, interface vendor and recognition sources remain in Details and
copied details. Missing MAC messages appear in Details rather than every list
row. Known MACs remain visible. IPv4 still appears above IPv6.

A product-like hostname or service name alone never establishes hardware.
Unidentified endpoints use “Unidentified device”; useful reported names remain
visible. Cosmetic hostname formatting removes a matching `.local` suffix and
separates hyphens, without asserting a manufacturer or model. Service categories
such as casting and file sharing describe the observed service only.

## Broad, free catalogue coverage

The bundled global table contains **256,338 exact manufacturer/model aliases**,
**86,540 distinct product titles** and **5,263 brand keys**, plus the existing
156 Apple Recog codes and the reviewed profiles below. These are catalogue
counts, not a claim that a scan can identify every listed product or that every
alias is a different retail model. The data covers worldwide manufacturers and
hardware beyond Android: printers, routers, computers/servers, storage devices,
cameras, televisions, consoles and industrial equipment are represented.

The two broad sources are:

| Source | Inspected evidence and access | Included scope and limits |
| --- | --- | --- |
| NIST Official CPE Dictionary | Official documentation permits voluntary use and says the dictionary is not subject to US copyright. Official API hardware snapshot retrieved on 2026-11-14; XML download returned HTTP 403 here. Direct API access succeeded. | 64,985 input hardware entries; software, deprecated/invalid/ambiguous entries and many component-only records are filtered. CPE is a product naming dictionary associated with security work, not a complete retail inventory or discovery fingerprint database. |
| Google Play public device list, via an MIT-licensed androidtrackers snapshot | Mirror commit and MIT notice inspected; all tuples cross-checked against Google's primary UTF-8-converted CSV. Public raw download succeeded after a connector transport failure. | 54,522 input rows; 156 mirror parsing mismatches, generic/unusable values and ambiguous brand/model pairs are excluded. Android-compatible hardware is only one part of the combined catalogue. |

The final merge rejects 600 ambiguous aliases, including 78 cross-source
conflicts. A second source cannot reintroduce a brand/model pair already known
to have multiple retail names in Google's primary list. Source counts overlap
where both independently agree: 81,272 retained aliases have Android evidence,
and 175,204 have NIST evidence. No commercial recognition database is bundled.
All source facts, source notices, hashes and generation rules are retained in
`android/catalog-original/`, the three catalogue provenance JSON files and
`scripts/generate-*-models.py`. Both build and host scripts verify the outputs
without network access.

Verified examples from the pinned data include:

| Reported manufacturer + Model | Canonical title | Coverage example |
| --- | --- | --- |
| Samsung + `SM-S918B` | Samsung Galaxy S23 Ultra | Phone |
| Google + `Pixel 8` | Google Pixel 8 | Phone |
| Brother + `HL-L6200DW` | Brother HL-L6200DW | Printer |
| NETGEAR + `R7000` | NETGEAR R7000 | Router |
| Lenovo + `ThinkServer RD650` | Lenovo ThinkServer RD650 | Computer/server |
| QNAP + `TS-469U` | QNAP TS-469U | Storage |
| Axis + `M1033-W` | Axis M1033-W | Camera |
| Sony + `BRAVIA 4K GB ATV3` | Sony BRAVIA 4K GB ATV3 | Television |
| Sony + `PlayStation 3` | Sony PlayStation 3 | Console |

The broad index requires **both a current reported manufacturer and an exact
reported Model**. A name, IEEE vendor, or globally distinctive-looking model
string alone cannot authorize it. Common explicitly reviewed corporate-name
aliases normalize manufacturer comparisons; the original manufacturer remains
unchanged in Details. CPE product spelling, its underscore-to-space equivalent
and the explicit English title without its brand may be exact aliases. CPE
version fields never supply a hardware revision. NIST-derived titles use the
brand and product token, borrowing English capitalization only for an exact token
match. Extra SKU/revision prose is discarded and those matches are labelled as
product families. Redundant brand prefixes are generated as explicit aliases
and undergo the same conflict checks; runtime lookup never invents an alias. Shared ambiguous aliases are
removed rather than assigned to whichever row happened to appear first.

Neither broad source supplies a reliable universal form factor. These entries
therefore retain icons/categories justified by current reported services; the
app does not guess a phone, router or camera category from a product name.
Reviewed profiles and Recog retain their more specific category icons. The
small reviewed table below is an override layer, not the entire catalogue.

The compressed runtime table is about 1.8 MB (22.5 MB uncompressed). A worker
loads it once as immutable public facts, using streaming UTF-8 validation,
32 MiB / 300,000-row / 804-character-line bounds and binary-search lookups. Scan
results and household observations are never stored in it. No app account,
paid API, per-scan charge or cloud recognition service is introduced.

NIST says its public XML dictionary is updated nightly. This app currently
bundles a pinned snapshot; it does not download a fresh dictionary every night.
An optional verified public-catalogue update could be added separately while
keeping recognition local and scans functional without internet. No device
identifiers need to be uploaded to download a public catalogue.

Primary sources: [NIST CPE](https://nvd.nist.gov/products/cpe),
[Google Play supported devices](https://support.google.com/googleplay/answer/1727131),
[licensed mirror](https://github.com/androidtrackers/certified-android-devices/tree/dc47abd4e6d16ebc7e4428b933b0a388a2ddf9f0).

## Catalogue and evidence policy

`DeviceCatalog.kt` contains three reviewed product profiles layered over that index, in addition to the
156 exact Apple model-code mappings from the pinned [Recog subset](RECOG.md).
Recog descriptions gain an Apple brand prefix where needed for the display title.
Its source-specific input admission and conservative family descriptions remain.

| Canonical title | Accepted reported Model values (case insensitive) | Category / precision | Reference |
| --- | --- | --- | --- |
| Hikvision DS-7608NI-I2/8P | `DS-7608NI-I2/8P`, `Hikvision DS-7608NI-I2/8P` | Network video recorder; exact reported model | [Hikvision NVR manual](https://assets.hikvision.com/prd/public/all/doc/m000065352/UD39811B-A_Network-Video-Recorder_User-Manual_V5.04.000_20250425.pdf) |
| NVIDIA SHIELD TV | `SHIELD Android TV`, `SHIELD TV`, `NVIDIA SHIELD Android TV`, `NVIDIA SHIELD TV` | Streaming device; family only, no year or Pro variant | [NVIDIA SHIELD TV](https://www.nvidia.com/en-us/shield/shield-tv/) |
| Sony PlayStation 5 | `PS5`, `PlayStation 5`, `Sony PlayStation 5` | Game console; family only, no edition or revision | Current PS5 discovery response described below |

These are exact aliases, not substring matches or an imported consumer database.
Bare `SHIELD` is only a redundant display name once a typed model has identified
the family. A model or manufacturer alone is never used to fill in the other's
reported field. The recognized brand stays inside the separate interpretation.
IEEE MAC vendor lookup remains independent and cannot authorize a product match.

Up to eight distinct model spellings, the first model/source, normalized
manufacturer and manufacturer source are retained per endpoint. Alias validation
waits for the required manufacturer and catalogue to be available, so equivalent
make-and-model and model-only reports work in either arrival order. Every retained
model must agree with the selected product; exceeding the bound suppresses recognition.
Conflicting specific model or manufacturer reports suppress recognition for the
rest of the scan, including when their display observations are evicted. Known
aliases of the same product may corroborate one another. Sources and values are
sanitized before entry. Progress copies retain independent evidence; fresh
endpoints inherit none. Group presentation may use a product only when every
member is compatible; per-address Details never borrow a neighbour's evidence.

## Read-only PlayStation discovery

A foreground IPv4 Wi-Fi scan sends at most two `SRCH` broadcasts to UDP 9302,
using protocol version `00030010`. The local receive socket binds to the selected
Wi-Fi address and one available port in 9303–9319, then to the selected Android
Network. No IPv4 broadcast is attempted on /31, /32 or IPv6-only networks.

The receiver runs for at most two seconds inside the existing scan deadline,
with receives bounded to 250 ms, 64 packets, 4,096 bytes per reply and 32 unique
responders. Only in-scope peers replying from UDP 9302 can be admitted. The parser
requires `host-type:PS5`, the exact protocol version and status 200 (ready) or 620
(standby). Duplicate critical headers, malformed or oversized replies are rejected.
A valid current reply establishes current presence; cancelled or late callbacks
cannot change results. The socket is tracked by normal scan cancellation and
closed on every exit. Source errors or truncated windows remain visible in the
scan report; a timeout without a console response is not a device identification.

A bare `PS5` Model from another protocol requires a reported Sony manufacturer;
otherwise the current PlayStation host-type response is required. The optional
reported host name and ready/standby state are retained. Host ID,
running app and system version are discarded. No MAC or grouping key is derived
from the console ID. This sends no wakeup, registration, credential, account or
control command, and adds no permission, runtime dependency or persistent state.

Wire constants and fields were checked against the primary implementation's
[discovery header](https://github.com/chiaki-ng/chiaki-ng/blob/main/lib/include/chiaki/discovery.h)
and [discovery source](https://github.com/chiaki-ng/chiaki-ng/blob/main/lib/src/discovery.c)
on 2026-11-14. The parser, bounded receive engine and Android integration here are
independently written; no implementation or database was incorporated from that
project. The two new icons are original Lucide server/gamepad-2 artwork, converted
with the existing script and recorded in `android/lucide-provenance.json`.

## Validation

Host suites exercise exact aliases, hostile/ambiguous inputs, model/brand
conflicts, family precision, bounded receive traffic, wrong ports and scope,
deadlines, cancellation, duplicate responses and 10,000 malformed packets.
The `device-identities` Android mode uses synthetic fixtures without an Android
Network; it cannot scan household devices. It checks actual Device observation
eviction, copies, grouping, current-console callback admission, raw-field
separation, list titles, icons, search, clipboard, accessibility and Back at
320dp/411dp, normal/200% text and both appearances. Its screenshots contain only
synthetic fixtures. The existing Recog and typography modes cover Apple models
and complete IPv4/IPv6 wrapping.

Physical PS5 discovery, actual household devices, TalkBack operation and scan
performance on a phone remain unverified. Recognition coverage is deliberately
limited; these changes do not resolve the existing discovery limitations in
[STATUS.md](STATUS.md). Verification results for this change are recorded there.
