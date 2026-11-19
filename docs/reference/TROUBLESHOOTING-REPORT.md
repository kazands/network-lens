# Troubleshooting reports

Open **Scan details → Troubleshooting report** after a real scan finishes or is stopped. Choose whether to include device names and local addresses, preview the report, then choose **Share full report**. Android opens your chosen receiving app with one text attachment containing every report page. **Copy this page** is a fallback for a support conversation that does not accept attachments.

The report includes every retained device group and address, including unnamed devices and the scanning phone. Search filters do not change its contents. It includes the app/build/API, scan status and coverage, method outcomes, reported identity, current product interpretation, conflicts, services, ports, presence evidence, grouping relationships and recorded lookup outcomes. An identified product is an interpretation, not verified ground truth. A stopped scan remains labelled stopped.

## Returned evidence and interpretation

Identity observations show their source, priority and whether their value was selected for display. Other returned fields appear separately and do not affect recognition. For example, a Samsung response with firmware but no acceptable model remains available for troubleshooting; it does not become a confirmed television identity merely because it appears in the report. Scanner and playback-group information remains separate from host identity.

| Response family | Additional diagnostic coverage |
| --- | --- |
| Bonjour/mDNS | TXT field names; reviewed model, manufacturer, version and capability values; explicit notices for withheld values |
| Cast, Samsung, Shelly, WLED, Spotify | Bounded JSON field paths and reviewed identity/capability/error values, including valid responses rejected by the identity parser |
| UPnP and eSCL | Bounded XML element paths and reviewed model/manufacturer/service-type values, including nested descriptions; attributes and other values are withheld |
| Apple identity | QueryType/GetValue fields, recognised error codes and valid model codes; other values withheld |
| HTTP identity lookups | Previous candidate failures plus the final state, protocol, port, TLS use, HTTP error status and attempt duration when available |
| Legacy identity and audio readers | Recorded state transitions and retained decoded observations; audio IDs are replaced consistently so relationships remain traceable |
| Other vendor/binary readers | Existing decoded facts and available outcomes; unused raw payload fields are not captured |

## Explicit limits

This is not a packet capture. HTTP headers, raw packets, TLS certificates, unused binary/vendor/audio payloads and unrequested information are not retained. IPP asks only for make/model. Malformed or oversized responses may have an error/size notice rather than decoded fields. Some transport-internal retries and discarded packets have only scan-level counters. No recorded attempt does not mean a method was never attempted. Silent devices cannot appear merely because they are present on the network.

Each endpoint retains up to 128 distinct diagnostic entries, independently of the smaller display limits. Retained exact duplicates are coalesced. Identity observations, lookup outcomes and parser-capture failures take precedence over other returned fields, which take precedence over withheld values. At capacity, a higher-priority event replaces the oldest lowest-priority entry; equal-priority events keep the earlier history. Displaced and rejected events are counted explicitly; they are not a count of unique missing fields. Copying or grouping devices preserves the journal and its omission count; only newly recorded events can increase it. Source/field/value lengths are bounded at 128/128/256 characters, with shortening labelled. HTTP lookup history retains up to 64 previous attempts plus current protocol states and reports overflow. Existing parser and discovery admission limits remain; capture does not bypass them. Arrays are summarised with an item count instead of retaining their contents.

The whole report has no device-count export cap. Preview and clipboard pages are at most 12,000 characters; sharing streams all pages. A failure preparing the report produces an error, not a silently shortened attachment.

## Privacy and lifecycle

Default reports replace known personal/device names, hostnames, addresses and identifiers with consistent labels that apply only within that report. The optional names mode includes network names, hostnames and IP/MAC addresses; serials and unreviewed raw values remain withheld. Unknown fields are named where safe, with value-withheld notices. Reviewed model, firmware and service text is still device-supplied: inspect the preview before sharing because it may contain custom information. This is data minimisation, not a guarantee of anonymisation.

Masking uses field roles: reviewed model/manufacturer values remain available when they exactly equal a factory device name, and numeric firmware versions such as `1.2.3.4` are retained as versions. Known address references remain masked even in version fields. Names embedded in custom identity text, URLs, credential assignments, email addresses and UUIDs are still filtered. Only titles sourced from reviewed bundled data receive the catalogue-name exemption. Titles assembled from device-reported models use normal name masking, including both group identification and endpoint product matches. New product constructors default to device-supplied title handling. Replacements operate on the original text once, so short names cannot rewrite generated privacy labels.

The app holds diagnostic evidence in RAM for the current scan. No report is saved automatically or uploaded to a recognition service. Sharing grants the chosen app read access to a RAM-backed text attachment for up to ten minutes. Starting a new scan or detecting a network change clears the temporary attachment. If another app is open, Network Lens checks for network changes when you return; the attachment may remain available until then or until it expires. Process death also removes it. Reads are bounded and the provider accepts neither writes nor arbitrary paths. A recipient may retain a copy after reading it; the user controls that choice. Clipboard copies are managed by Android.

The older **Copy recognition report** remains a separate counts-only summary. It does not contain the detailed evidence described here.

## Verification

The implementation passed a fresh independent Deep code review with no substantive findings. Regression coverage includes masked and explicit-name reports, product-title provenance, snapshot isolation, exact omission counts, lookup history, lifecycle invalidation and temporary sharing.

See [0.3.29 release verification](../archive/VERIFICATION-0.3.29.md) for exact APK hashes, checks and limits. These are synthetic checks; receiving-app compatibility, physical TalkBack, real firmware behaviour and low-memory performance need broader testing.

An optional Cast group-information reply that supplies no supported information is
a finished check with an explicit “optional information not reported” count. It
does not create an unfinished scan warning. Actual transport errors, malformed
responses and deadlines remain visible and cannot be cleared by a later optional
absence. This reporting change does not improve the receiver's response.

Group counts describe displayed address groups, not a verified number of physical
boxes. Separate audio zones can refer to one amplifier. A journal overflow is a
count of recording events, not necessarily unique missing response fields.


### Identifier and capacity corrections (0.3.29)

Opaque UPnP `uuid:` identifiers are masked even when they are not standard UUIDs and even when names/local addresses are included. Protocol service URNs remain visible.

The 128-entry journal keeps identity/outcomes ahead of ordinary observations and withheld values. Within an equal-priority tier, a source holding more than 16 records can yield capacity to a less-represented reader. Reports include omitted-event counts by priority; these count rejected/displaced events, not unique missing fields. Bounded retention still cannot guarantee every returned field is retained.
