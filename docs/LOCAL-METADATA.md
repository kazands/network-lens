# Local device metadata

The source includes the following additions after the 0.3.11 preview. These are
device-reported observations, not cloud recognition or authenticated hardware
identification. Every scan starts fresh and all metadata remains in memory.

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

The existing 55-second scan-work deadline remains. HTTP metadata has two workers,
32 queued tasks, at most 32 attempts per protocol and 96 overall, with one attempt
per peer/protocol. Changing advertised paths/ports cannot add attempts. Each
exchange has a maximum two-second deadline within the remaining scan budget, a
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
and IPP lookup outcomes, labelled by protocol, IPv4/IPv6 and the attempted port
(with TLS for IPPS). Success without an optional model/manufacturer is explicit.
Connection refusal and routing failures require typed platform evidence; generic
connection failures remain generic. Read timeouts, HTTP errors, invalid metadata,
work limits, unfinished lookups and Stop remain distinct. A failed lookup does
not revoke independently confirmed presence or change Partial reporting.

The same panel identifies other current observations supplying that endpoint's
displayed model/manufacturer. It does not borrow a grouped address's details,
guess a brand from a MAC vendor, or issue additional fallback queries. Alternative
sources are evaluated from current observations, so a later local response can
appear alongside an earlier HTTP failure. Unknown values remain unknown.

These outcomes have four slots per endpoint, live only in session memory and are
copied with progress/retained device snapshots. Stop/finalization seal pending
outcomes against late worker updates. A fresh scan has no inherited outcomes.
The explicit Copy device details action includes the source-labelled outcomes;
there is no automatic export, logging or persistence of endpoint diagnostics.

The outcome increment from `dbf7828` passed 22 host suites / 16,209 checks, direct
app/instrumentation builds and packaged permission/security audits. API 37 passed
five modes / 987 checks; API 36 passed four modes / 956 checks. Both ran metadata,
typography, failure-reporting and session-only; API 37 also ran fresh-presence.
A fresh independent Deep Kaz review approved the complete increment with no
substantive findings. Its independent host execution reached nine passing suites
before a sandbox loopback restriction; the coordinator completed all 22.

The tested development APK SHA-256 is
`b2a5dcc204cb671600f4583b8cc1710ee5619ebb164986d4b777ba3656674766`.
The private matching-signed phone candidate SHA-256 is
`33caf50942b4f006aaac99faf39b127815e89c20a5e78a708ff74d280cbae650`;
its application payload matches the tested APK. Existing release outputs were
restored byte for byte. The authorized Pixel 8 verified installed bytes, preserved
grants and empty startup. A fresh scan reached Partial after 36.12 seconds
including UI observation. The foreground guard then stopped the journey before
endpoint-outcome capture or Stop; those checks remain incomplete. An earlier
handoff stopped before installation and performed no scan. No release was
published. These observations do not establish a discovery fix.

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

The reliability pass separates each configured receive window into at most 4,096
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
bounded. The physical measurements below establish behavior in the observed
scans; they do not establish complete device coverage or elimination of
intermittent failures.

## Development validation (2026-10-31)

The checks below validate bounded parsing and transport behavior. Physical-device
results and their remaining limits are recorded separately below. Keep the pull
request as a draft while the cause of Spotify connection failures remains
unresolved.

Before the diagnostic extension, the source including the Gen1 short-ID
correction passed:

- `scripts/test.sh`: 20 suites, 14,131 checks, including 324 metadata checks and
  malformed-input/loopback HTTP/IPP/TLS fixtures. The new short-ID regression
  failed against the old matcher before the fix.
- `scripts/build.sh` and `scripts/build-android-tests.sh`: passed using full JDK
  21 and the pinned toolchain. Packaged permission/security audits passed; no
  permission or runtime library was added.
- API 36 `local-metadata`: 24 checks, with installed app/test APK hashes verified.
  This includes short-ID admission through a current advertisement without
  treating the ID as a MAC.
- Whitespace and repository hygiene checks, including changed/new working-tree
  files. Local logs and emulator artifacts remain ignored.

Development APK SHA-256 for the short-ID correction:
`0da7d67015642d4c903f102c3bdd59013310e487c40379bccad3622225ed5e40`.
Instrumentation APK SHA-256:
`8f53439b91378bb8a0bd23a292f59aede58c7111591d9686c6d81127deba64d7`.

Before the short-ID correction, the reviewed implementation also passed:

- 14 focused emulator suites: API 26 (133 checks), API 36 (91) and API 37 (80).
  These covered metadata, discovery/freshness, legacy resolution and API 37
  local-network permission behavior. They were not rerun on API 26/37 after the
  correction.
- 28 additional API 36 integration checks using real selected-Network-bound HTTP
  against a synthetic server on the emulator's host-loopback alias. All four HTTP
  protocols populated device details; authentication failure, cancellation after
  server receipt, socket cleanup and fresh-instance isolation were checked.
  Service/presence inputs were synthetic, so this was not real-device discovery.
- Matching-certificate IPPS success and mismatched-identity rejection in separate
  host JVM loopback fixtures. This does not establish trusted-IPPS success on
  Android hardware.

That earlier review used development APK SHA-256
`979975c1cc32ea7888503296c2017b70ce7f881bafa8a1f5cc70b317d78bf7e2`.
A fresh independent Deep review found one minor Gen1 short-ID compatibility gap;
the current source includes its correction and regression coverage.

## mDNS reliability validation (2026-10-31)

A mixed-traffic regression against the prior receiver failed at response 125 after
900 query packets. The revised receiver accepts the same 900 queries plus 500
responses without exhausting its independent response budget. Synthetic tests
also cover response/raw-packet/byte flood limits, expiry/goodbyes, family isolation,
query non-authority, bounded cache admission and multipart known-answer framing.

The reliability implementation passed 21 host suites / 15,790 checks, direct app
and instrumentation builds, packaged permission/security audits and five API 36
suites / 159 checks: local-metadata 28, split-mdns 22, service-retries 9,
failure-reporting 37 and scan-details 63. Installed hashes were verified. A
mistyped test mode ran the default smoke suite; its 67 checks are not counted as
retry coverage or in the 159-check total. A fresh independent Deep Kaz review
approved the complete mDNS change with no substantive findings.

Two controlled foreground measurements on the authorized Pixel 8 (API 37)
used a private matching-signed candidate based on `9df32e6`, plus bounded
TCP-only Spotify retry counters. Both scans remained Partial, after 37.61 and
38.34 seconds including UI observation. The complete method reports were captured.
The main direct mDNS method was OK in both: 1,125 and 1,140 aggregate datagrams,
430 and 453 response decodes, 18 and 4 coalesced questions, and 95 and 104 known
answers sent. Neither run reached a receive limit. These are traffic/work counts,
not device counts; they do not prove an overall traffic reduction or full coverage.
The second scan still reproduced native HomeKit and HTTP resolution deadlines.

Each scan recorded an IPv4 Spotify connection failure that remained a timeout
through additional TCP-only attempts capped at 400 ms and 1,000 ms; an IPv6
failure was actively refused on both additional attempts. Retries used the same current peer,
advertised port and selected Wi-Fi, within the original request deadline. Other
Spotify endpoints returned valid metadata. A separate bounded computer probe on
the directly attached LAN found four current IPv4 Spotify endpoints: three opened
within 400 ms; one timed out at both 400 and 1,000 ms. Aggregate-only evidence does
not establish that it was the identical endpoint observed by the phone, but it
shows that an advertised Spotify port can also be unavailable from another client.
No HTTP response was received for these connection failures, so they do not
establish an authentication or metadata-parser defect. Longer/repeated connections
have not demonstrated recovery and are not added to production policy.

A second private experiment replaced only the API 37 first resolution attempt
with an exact-instance `DiscoveryRequest`, bound to the selected Network with
`FLAG_NO_PICKER`; the second attempt retained the existing service-info API. Two
fresh scans took 40.46 and 35.93 seconds including UI observation. HomeKit and HTTP
method reports were OK in both; direct mDNS also avoided its receive limits.
Spotify connection failures still kept both scans Partial. Because native failures
were intermittent even before this experiment, two successful runs supported
trying the bounded alternative but do not establish a permanent cure or its root cause.

The extended matching-signed trial from `771a281` then ran two more fresh scans
(37.53 and 38.89 seconds including UI observation) plus Stop scan. The first
returned usable HomeKit/HTTP data. The second reproduced addressless resolution
failures for both types through the filtered request and its service-info
fallback. Both full scans avoided the direct mDNS receive limit and remained
Partial. Stop scan reached terminal Partial after 7.11 seconds including UI
observation, with complete method diagnostics captured. These results do not
establish the alternative API as a reliable workaround in this environment.

The resolver change and its extra tests were reverted in `59396a0`. The retained
application, tests and build scripts are byte-for-byte the committed `9df32e6`
implementation. No additional native discovery path or Spotify retry remains.
The [Android filtered-discovery API](https://developer.android.com/reference/android/net/nsd/NsdManager#registerServiceInfoCallback(android.net.nsd.DiscoveryRequest,%20java.util.concurrent.Executor,%20android.net.nsd.NsdManager.ServiceInfoCallback))
is available, but availability and correct API use do not guarantee responses.
The existing direct-current-response recovery remains; unresolved native
candidates are not silently promoted or relabelled as successful discoveries.

Private experiment source and APKs remain ignored. Prior release outputs were
restored exactly after each preparation. The PR remains draft; neither the
Spotify failures nor intermittent native failures are claimed solved.

## Rejected native resolver experiment validation (2026-10-31)

Before the physical recurrence, `771a281` passed 21 host suites / 15,790 checks,
direct development/instrumentation builds and packaged permission/security audits.
API 37 passed six suites / 130 checks: permission 17, resolver-upgrade 23,
local-metadata 28, split-mdns 22, service-retries 9 and fresh-presence 31. API 36
passed four suites / 80 checks: resolver-upgrade 21, local-metadata 28, split-mdns
22 and service-retries 9. Installed app/test hashes were verified. These tested
correct API use, deadlines, fallback, cleanup and admission; they did not establish
real-device resolution reliability. API 26 was not rerun for that experiment.
A fresh independent Deep Kaz review approved code correctness with no substantive
findings; runtime verification remained the coordinator's responsibility.
The two matrices belong to the rejected experiment, not the retained mDNS build.

The synthetic Network fixture was corrected to `Network.fromNetworkHandle` before
those passing runs. Initial emulator attempts stopped before tests because the
emulators were unavailable or still booting. Final matrices used fully booted
isolated emulators with software graphics; interruptions are not counted as passes.

Rejected development APK SHA-256:
`95b93e6d58dc9499f1eaa7a490274274d6af93a685537d749decc549c20c7b71`.
Rejected instrumentation APK SHA-256:
`2acf291b1fe1f4b38fb823a27eb04dbde0bc11273756e362dea7ebb8f32f6e36`.
Rejected signed phone candidate SHA-256:
`11b453df3592903766ea056d40435c7e3e0c5040fb430716c03022c4214f5818`.
These are historical trial artifacts, not the retained candidate.

## Native and wire correlation diagnostics (2026-11-01)

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

Automated validation passed: 22 host suites / 16,079 checks, including 289 new
correlation checks; direct app/instrumentation builds and package audits; API 37
five suites / 141 checks (resolver-upgrade 21, failure-reporting 39, split-mdns 22,
local-metadata 28, fresh-presence 31); API 36 four suites / 110 checks (the same
suites except fresh-presence). Installed app/test hashes were verified. API 26 was
not rerun for this increment. A fresh independent Deep review identified a minor
late-completion expiry gap. Its new regression failed before the correction;
bounded diagnostic completion timestamps fixed it, and targeted independent
verification confirmed the finding resolved without a verified regression.
Physical measurements and their limits are recorded below.

## Correlation phone validation (2026-11-01)

The authorized Pixel 8 (API 37) received the reviewed diagnostic candidate from
`e892814` as an in-place update. Installed APK bytes matched the signed artifact,
permission grants were preserved, and startup was empty without automatic scanning.
Application payload matched the development APK tested on the emulators. Existing
release outputs were preserved and restored byte for byte; no release was published.

Signed diagnostic candidate SHA-256:
`6f5a51bbecf0566d9c456fff58d66de66eedfc1227331e4b490b34c1a80631d0`.

Three full foreground scan reports were captured, including every advertised
method row. They reached Partial after 37.27, 36.87 and 36.27 seconds including UI
observation. All three reported native HomeKit/HTTP and direct mDNS as OK; none
hit a receive budget. Aggregate datagrams were 1,161 / 1,102 / 1,191 and response
decodes 458 / 443 / 456. IPP metadata succeeded in all three. Spotify returned
3 / 3 / 2 metadata replies from 5 / 5 / 4 requests; each report recorded a connection
timeout and a connection failure. These failures retained Partial status.

No terminal native failure occurred in these full scans, so real-phone failure-time
wire categories and late-reply classifications were not exercised. This does not
establish a fix or distinguish stale cache from missed/silent replies. Controlled
native-failure and late-wire Android fixtures cover those paths separately.
A separate Stop check reached Partial after 16.26 seconds including UI observation;
its full method report marked unfinished work stopped/incomplete. There was no
terminal native failure in that stopped report either.

Reader interruptions are not counted as extra successful measurements. An earlier
37.60-second scan had no retained complete report and was excluded. The faster
reader failed its row-completeness check; a later no-new-row shortcut could also
stop inside a long diagnostic card. Bounded forward/reverse reads through dialog
padding replaced those shortcuts. A read-only check confirmed one display and
identified the chat app as the input-focus owner while Network Lens remained
visible. The foreground guard was kept unchanged; report capture resumed only
with Network Lens focused. No foreign app content or household identifiers were
retained or published.

This reviewed diagnostic build remains installed. It adds evidence for the next
natural native failure; it does not add background scans, persistent logs or a
connectivity workaround. Native root cause, Spotify availability, broader hardware
coverage/model accuracy, trusted IPPS, physical Home/Wi-Fi replacement transitions
and a fresh TalkBack audit remain unverified.

## Retained reliability candidate validation (2026-10-31)

After rejecting the native trial, `59396a0` restores application/test/build-script
contents identical to reviewed `9df32e6`. The rebuilt app and instrumentation
passed direct builds and package audits. Two focused API 37 suites passed on the
rebuilt artifact, with installed hashes verified: resolver-upgrade 17 and
local-metadata 28, for 45 checks. The retained source's broader 21-suite host and
five-suite API 36 results remain the mDNS-stage results above; the rejected
experiment's 210 emulator checks are not counted as retained functionality.

Restored development APK SHA-256:
`169b52f94c93827f9d03bc85345f941866b5a05f7e402c2429e55e1a0de2730c`.
Restored instrumentation APK SHA-256:
`e5e7981b0f79d857165cd0dc269650316d68a31b64347dbbc8b154af15aaf46a`.
Matching-signed retained candidate SHA-256:
`4e27b3d6b52ed71e9ed4c33e2e6724b7e9b5e0faeaea7e8b44008c5e4ddc3d9d`.
Its application ZIP payload matches the restored development APK. Prior release
outputs were preserved and restored byte for byte; no release artifact or tag was
published.

The authorized Pixel 8 received the retained candidate as an in-place update.
Installed bytes matched that signed APK, permission grants were preserved, and
startup was empty without an automatic scan. Its fresh foreground scan reached
Partial after 37.75 seconds including UI observation. The foreground guard stopped
report capture; after the user confirmed readiness, the intact report was captured
without reinstalling or repeating that scan. HomeKit, HTTP, direct mDNS and IPP
method reports were OK. Spotify returned three metadata replies from four
requests; one connection timeout kept the scan Partial. Direct mDNS counted 1,182
aggregate datagrams and 453 response decodes, coalesced 10 questions and sent 102
known answers. No receive limit was hit.
The complete method report contains no claim that every device was discovered.

A separate Stop scan check reached terminal Partial after 6.82 seconds including
UI observation; its complete method report was captured. Unfinished methods were
explicitly marked stopped/incomplete. Connection failures already observed before
cancellation remained visible. That retained candidate is historical; the correlation diagnostic candidate above
supersedes its installation state. No private connection probe or rejected resolver
path is part of either retained build. Deliberate
physical Home/Wi-Fi replacement transitions, per-device model accuracy, broader
hardware families, trusted IPPS and a fresh visual/TalkBack audit remain unverified.

## Diagnostic extension validation (2026-10-31)

The diagnostic extension passed:

- All 20 host suites / 15,243 checks, including 1,436 metadata checks. New tests
  exercise numeric HTTP errors, malformed/truncated/oversized HTTP, unusable
  metadata, connection/write/read failures, TLS failure, deadline-clamped read
  timeouts and cancellation after a real loopback server receives the request.
- Three additional focused metadata repetitions passed, including the corrected
  deadline/read-timeout and in-flight cancellation cases.
- Direct app and instrumentation builds with JDK 21; packaged permission/security
  audits passed.
- API 36: `local-metadata` (28 checks), `failure-reporting` (35), `scan-details`
  (63) and `split-mdns` (22): four suites / 148 checks, with installed app/test
  bytes verified. The reporting checks include large text and both appearances.
- A fresh independent Deep Kaz review of the complete diagnostic change:
  Approve, with no unresolved substantive findings. The reviewer's own runtime
  attempt was blocked by its restricted socket environment; passing runtime
  results above come from the coordinator's supported test execution.

Development APK SHA-256: `e091d3586849c03f8a64798d86c4a03122ca4d434b400e37f7dffc3571ebeee9`.
Instrumentation APK SHA-256: `b92731e875e108470d1176e51072d6a66ef6aedf6ced8721f6d30fd11fec3b76`.

An exact timeout-category regression initially failed: a read timeout clamped by
remaining budget could fire fractionally before the absolute deadline. The final
code tracks that clamping for classification without changing timeout values.
The final host run above includes the correction. Physical diagnostic retesting
is recorded separately from the earlier phone candidate below.

## Diagnostic phone retest (2026-10-31)

The authorized Pixel 8 (API 37) received the diagnostic candidate from commit
`35a0a096fc4e673f03893d8534e8be4508bf65b8` as an in-place update. Installed bytes
matched the signed candidate, permission grants were preserved, and its updated
process started empty without an automatic scan. This is a historical test
candidate; the later reliability validation supersedes its installation state.

Signed diagnostic candidate SHA-256:
`a5ef00d4119b35862a05d985e6f465b2a85c78256cff80e581b2ac30f41a34f9`.
Its application payload matched the development APK tested above; prior release
outputs were again restored byte for byte. No release APK or tag was published.

Two fresh foreground scans reached Partial after 36.31 and 35.96 seconds,
including UI observation overhead. The complete method report was captured for
each scan. Both accepted IPP printer information and Spotify metadata replies.
Each also recorded a Spotify connection timeout; the second recorded an
additional connection failure. These failed attempts did not reach an HTTP
response, so their diagnostics do not show an HTTP authentication rejection or
invalid metadata. The underlying cause of the connection failures remains open.

Both scans reached the original IPv4 receive-window safety cap. About half the
aggregate mDNS datagrams had a non-response header. This confirms that the receive
budget includes substantial non-response traffic; it does not identify the
senders, prove duplicate queries or establish that raising the cap is appropriate.
The known native HTTP resolution failure did not recur in these two scans; it
remains an intermittent limitation, not a resolved issue.

Stop scan reached a terminal Partial report after 6.31 seconds including UI
observation. Full per-method cancellation diagnostics were not collected. An
initial attempt stopped at the foreground guard; after the user confirmed the
app visible, the helper restored off-screen scan controls before the two fresh
runs. Only that interrupted attempt's later terminal Partial state was checked.
No app crash entry appeared in the app-UID crash buffer checked after the initial
stop. This does not establish the cause of the transient foreground mismatch.

The guarded journey verified the update, fresh scans and terminal cancellation.
Raw UI remained in memory,
local aggregate evidence stayed ignored, and no instrumentation was installed
on the phone. Real per-device model/source-label accuracy, unobserved protocol
families, trusted IPPS, deliberate Home/foreground-loss and Wi-Fi replacement
transitions, and visual/TalkBack checks remain unverified for this candidate.
These diagnostics explain failure categories without changing discovery limits
or claiming the Partial scans finished all planned work.

## Initial metadata phone validation (2026-10-31)

An explicitly authorized Pixel 8 running API 37 received an in-place update from
its existing production-signed preview to a candidate built from source commit
`505aa0e78fbbb04b933f3dd2010eeb74b8bcfbe3`. The candidate matched the installed
app's signing identity. Installed APK bytes matched the prepared candidate,
Android permission grants were preserved, and the updated process opened ready
with an empty result list and no automatic scan. This is a historical candidate;
the later reliability validation supersedes its installation state.

Signed candidate APK SHA-256:
`18e92c575fddf67a83d891583fb7286ce3fc45810557496d1a8c71145193e277`.
Its application ZIP payload matched the tested development APK, excluding signing
entries. Previous release outputs were preserved and restored byte for byte.
This candidate has not been published as a release.

| Check | Observed result |
| --- | --- |
| Existing installed preview: baseline foreground scan | Partial, about 36 seconds; mDNS type/hostname queries reached the 1,024-packet safety limit. |
| Candidate: first fresh foreground scan | Partial, about 37 seconds; some advertised Spotify metadata was unavailable. |
| Candidate: second fresh foreground scan | Partial, about 36 seconds; Spotify unavailability repeated, and native HTTP service resolution exhausted both bounded attempts without usable addresses. |
| Candidate: Stop scan during discovery | Terminal Partial observed after about 7 seconds and remained stopped. Complete per-method cancellation diagnostics were not captured. |

Times include UI observation overhead and are not engine benchmarks or evidence
of a performance improvement. Every method diagnostic was captured for the
baseline and both full candidate scans. The cancellation report remained intact,
but the helper could not reach its disclosure through the long report; only its
terminal Partial/stopped state was verified. Test-helper traversal/startup
adjustments do not establish application defects.

Both candidate scans accepted IPP printer information and Spotify Connect
metadata replies. Other Spotify metadata attempts were unavailable in both scans;
the generic diagnostic does not distinguish authentication, connection, response
or device behavior, so the cause is unresolved. Shelly and WLED metadata queries
were unused because no supported service was advertised. Successful mDNS query
status alone does not establish that Thread or ESPHome hardware was present or
that a retail model was identified correctly. Per-device field accuracy and source
labels were not checked on this phone.

The helper verified the specific authorized phone and focused-window owner before
and after UI operations, stopping on foreground loss. No instrumentation was
installed on the phone. Raw UI stayed in process memory; only local, ignored
aggregate evidence and fixed method diagnostics were retained. Public notes omit
household inventories and identifiers.

The following remain unverified for this candidate:

- Real Thread/ESPHome/Shelly/WLED compatibility and per-device model/source-label
  accuracy; broader device coverage and repeatable latency measurements.
- Trusted-IPPS success on Android hardware.
- Home/foreground-loss and Wi-Fi replacement transitions on this physical
  candidate; the guarded journey does not exercise those transitions.
- Full per-method cancellation diagnostics and a visual/TalkBack audit.

The existing intermittent HomeKit/HTTP resolution limitation remains open; HTTP
resolution failed again in the second candidate scan. Keep the PR draft pending
investigation of the unavailable Spotify metadata. Passing update, transport and
reporting checks does not make these Partial scans Complete.

## Protocol references

The additions independently implement documented wire behavior. No vendor code,
fingerprint database or additional runtime dependency is incorporated.

- [Multicast DNS traffic reduction](https://www.rfc-editor.org/rfc/rfc6762.html#section-7), known-answer and duplicate-question suppression.
- [Thread Group border-router white paper](https://www.threadgroup.org/Portals/0/documents/support/ThreadBorderRouterWhitePaper_07192022_4001_1.pdf), section 9.
- [ESPHome mDNS implementation](https://github.com/esphome/esphome/blob/5f5a81434df4d47c3b9fb8d3927f11791a5bcbf6/esphome/components/mdns/mdns_component.cpp), TXT field definitions.
- [Shelly mDNS](https://shelly-api-docs.shelly.cloud/gen2/General/mDNS/), [Gen1 compatibility](https://shelly-api-docs.shelly.cloud/gen2/General/gen1Compatibility/) and [Gen1 `/shelly`](https://shelly-api-docs.shelly.cloud/gen1/#shelly).
- [WLED JSON API](https://kno.wled.ge/interfaces/json-api/) and [WLED mDNS advertisement](https://github.com/wled/WLED/blob/fce41f2655c3087d3eaf95c028d2856967cbc86e/wled00/wled.cpp).
- [Spotify ZeroConf API](https://developer.spotify.com/documentation/commercial-hardware/implementation/guides/zeroconf), Get Information.
- [RFC 8011](https://www.rfc-editor.org/rfc/rfc8011.html), sections 4.2.5 and 5.4.9; [PWG IPP guide](https://www.pwg.org/ipp/ippguide.html), printer discovery and resource paths.
