# Public identity readers

These readers look for useful clues on unresolved endpoints that have responded
in the current scan. They do not turn a generic web server, certificate name or
SSH banner into a hardware model, manufacturer, MAC address or grouping link.
Available in Network Lens 0.3.35 and newer.

## Admission and transport

Discovery requires the same endpoint object, a fresh response, the selected
Wi-Fi and eligible scope. This phone is excluded. A compatible product or
specific reported model suppresses new work, including work still queued when
the stronger response arrives. Cached native advertisements cannot start these
readers. Network loss, cancellation and the scan deadline retire their work;
late results cannot change a terminal journal or a replacement endpoint.

| Reader | Opportunity | Retained evidence |
| --- | --- | --- |
| Public HTTP | Up to two observed/advertised ports from 80, 8080, 443, 8443 | Status, Server, one unambiguous authentication realm, one bounded HTML title |
| Certificate observation | One observed/advertised port from 443, 8443, 8009 | Unverified CN, organisation and up to eight DNS/IP alternative names |
| SSH | Observed/advertised port 22 | Software token in the server greeting, excluding comments |
| Direct LLMNR | Unresolved current endpoint without a hostname | One correlated reverse-PTR hostname from that endpoint over TCP 5355 |

No extra web/SSH port sweep is added. LLMNR is a direct reverse lookup following
[RFC 4795 section 2.4](https://www.rfc-editor.org/rfc/rfc4795.html#section-2.4),
not a general multicast inventory. It validates transaction, question, response
flags and hostname syntax. A refusal or timeout means no supported LLMNR reply;
it does not prove a device is absent. Router DNS names remain diagnostic only.

HTTP makes one `GET /`; no redirects, credentials, cookies, linked resources or
scripts are followed. Headers are limited to 16 KiB; a generic HTML body is an
8 KiB prefix. A title must be complete within the prefix. Authentication/error
responses retain only selected headers and do not read the body. Multiple
authentication challenges are bounded; differing realms remain ambiguous.
Normal HTTPS retains platform certificate and IP verification.

Port 80 reuses the existing equipment reader's root request rather than fetching
the same page again. Public admission first gives that reader a chance to reserve
the root, including when WS-Discovery supplied port 80 before a different mDNS
service arrived. Equipment admission and journal reservation share the endpoint
lock. A previously claimed root cannot start another equipment fetch or reset its
timing. If the equipment admission cap is exhausted, the bounded public fallback
remains available; a rejected equipment worker remains an explicit work limit. The existing exact TP-Link server and corroborated page
markers remain necessary for its public identity POST. That equipment-specific
page can still use the original 64 KiB bound. Reuse is labelled in the journal;
a later equipment-identity failure cannot erase already received root clues.

The separate certificate observer captures bounded fields from a presented chain
and then rejects it. It never finishes a trusted TLS session or sends application
data. Input/output, buffers, chain size, handshake steps and duration are bounded.
Certificate serial-number fields, keys and raw certificates are not retained.

Subject labels are selected from canonical RFC2253 output with a single bounded
forward scan, including escaped separators and UTF-8 hex escapes. Subjects over
2,048 UTF-16 code units are rejected whole; each decoded CN/organisation label
is limited to 160 UTF-16 code units. Duplicate, unsupported or overlong selected
attributes are omitted with a fixed diagnostic notice. Malformed subjects are
not used. No backtracking regular expression runs on a certificate subject.
SSH receives at most eight bounded lines and sends no client banner, key exchange,
authentication or commands. Its software label follows the greeting structure in
[RFC 4253 section 4.2](https://www.rfc-editor.org/rfc/rfc4253.html#section-4.2).
Connections may appear in device access logs.

## Scheduling and interpretation

A separate two-worker fair queue holds at most 32 pending peer tasks, with at
most 32 endpoints, 96 admitted requests overall and 32 per reader/port. Each
peer gets at most five requests, an original four-second window and at most
two seconds per request, all capped by the scan deadline. Late advertisements
do not extend that window. Existing metadata workers keep their own queue. The
equipment reader can also retain clues from its existing root request for an
already identified endpoint; this does not start an additional public request.

Only reviewed, corroborated page signatures can add software-family evidence:
Home Assistant, Pi-hole, Tasmota or OctoPrint. A title alone or a generic Server
header cannot do so. These families have no physical device type. A software
service can run on a computer, appliance, proxy or container; this does not
identify the hosting hardware. Other clues remain available for investigation.
The signatures are original small rules, informed by official
[Home Assistant frontend markup](https://github.com/home-assistant/frontend/blob/dev/src/html/index.html.template),
[OctoPrint frontend markup](https://github.com/OctoPrint/OctoPrint/blob/master/src/octoprint/templates/index.jinja2),
[Pi-hole page structure](https://github.com/pi-hole/web/tree/master/scripts/lua) and
[Tasmota WebUI documentation](https://tasmota.github.io/docs/WebUI/). These
interfaces vary by version and customisation; an absent marker is not absence
of the software. No upstream application code or database is bundled.

Each endpoint has an eight-entry public journal, terminal states and an overflow
count. Supported metadata and public readers record queue wait, budget remaining
at start and elapsed request time separately. Durations do not include absolute
system uptime. These measurements help distinguish absent responses, queue
pressure and interpretation gaps; they do not establish accuracy.

## Reports and validation

Technical details and explicit device-detail copies show selected raw clues.
Troubleshooting reports mask those clues by default. The explicit names/local
addresses/public-labels option includes only reviewed fields; raw unknown values,
cookies, nonces and keys remain withheld. Counts-only recognition reports include
fixed protocol outcomes without raw labels. New scans and process exit clear the
in-memory evidence, with no history or cloud recognition.

Host regression tests use synthetic parsing and loopback HTTP/SSH/TLS fixtures.
The emulator-only `public-identity` mode checks ownership, cancellation, admission,
privacy, late model arrival, and separate TLS observation/HTTPS trust behaviour.
The optional host [public-page trial](PUBLIC-WEB-TRIAL.md) reports aggregate clue
availability. A bounded `ipv6-public-trial` emulator experiment evaluates one
selected-Wi-Fi `ff02::1` echo; it is not enabled in production discovery and does
not infer MACs from IPv6 interface identifiers.

Physical firmware compatibility, low-end performance and recall improvements
remain unverified. Credential-based SNMP remains a follow-up after these public
readers are evaluated. No new permissions or discovery databases are added.

See [candidate verification](../PUBLIC-IDENTITY-VERIFICATION.md) for exact hashes, checks, review findings and platform limits.
