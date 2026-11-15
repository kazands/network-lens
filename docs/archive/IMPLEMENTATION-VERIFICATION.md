# Implementation verification history

Historical results for the specific candidates described below. These are not
fresh test claims for the current download. See [current status](../STATUS.md)
and [release verification](../VERIFICATION-0.3.14.md) for the supported release.

## Clear device identities and global catalogue (0.3.13)

The source now puts canonical product names first and keeps useful custom network
names below them. It bundles **86,540 product/family titles across 5,263 brands**,
indexed by 256,338 manufacturer-qualified spellings, plus the existing 156 Apple
Recog codes and reviewed family/profile overrides. The broad sources are a
filtered public NIST CPE hardware snapshot and a pinned MIT-licensed Android
model mirror cross-checked against Google's public list. This is worldwide
hardware coverage beyond Android, including printer, router, computer, storage,
camera, TV and console examples. It is not a complete physical inventory or a
promise that every listed model can be identified by this scanner.

Broad matches require a current reported manufacturer and exact model; names,
MAC vendors and cached advertisements cannot supply them. Ambiguous aliases
and cross-source conflicts are excluded. NIST-derived names use only supported
product-family precision, discarding unsupported SKU/revision prose. Unknown
form factors keep icons justified by current services. Reviewed profiles select
a recorder icon for the Hikvision NVR, streaming icon for SHIELD, and controller
icon for PlayStation 5. Read-only bounded PS5 discovery supplies genuine console
model evidence; a PS5-like name alone cannot identify it. Missing-MAC explanations
remain in Details. Reported names/models, manufacturer, interface vendor and
recognition inputs/sources remain separate. Nothing is retained across scans.

The catalogue loads once from a compressed public asset on a worker, using
streaming bounded validation and binary-search lookups. It adds no paid licence,
account, per-scan request or cloud recognition. NIST updates its public XML
nightly; this app currently uses a pinned snapshot from 2026-11-14. An optional
verified online catalogue download is a separate possible addition; no device
identifiers need to leave the phone to fetch public catalogue updates.
See [DEVICE-IDENTITIES.md](../DEVICE-IDENTITIES.md) for data, notices, source access,
selection, evidence conflicts, grouping and PlayStation wire limits.

Initial feature verification passed all 26 host suites / 29,839 checks, direct
app/instrumentation builds and packaged permission/security audits. The catalogue suite also passed in a host JVM
limited to 128 MiB heap; this is not a phone memory/performance measurement.
That APK passed nine modes / 1,654 checks on each of API 36 and 37:
device identities (269), Recog (298), typography (829), local metadata (49),
session-only (39), fresh presence (31), navigation (31), HomeKit (69) and failure
reporting (39). Installed app and test APK bytes were verified. Native canonical
titles, icons, search, clipboard, full IPv4/IPv6 wrapping, family Details and
Back were checked at 320dp/411dp, normal/200% text and both appearances. Synthetic
screenshots were inspected; some include Android's transient clipboard preview
after explicit copy checks. An initial earlier matrix was obstructed by confirmed
System UI ANR dialogs on both test emulators; those were dismissed before the
successful reruns. The supplementary Impeccable source detector reported no
findings; native layout verification came from the emulator checks and images.

Initial implementation APK SHA-256:
`0e239ec12374ec7e27f73b9b53ba73c6eb7181fd41df720876bb9be856f6304d`.
API 36/37 instrumentation APK SHA-256:
`fc151daa79349e528bb23c8741f5903c8a786ea05694309fa60b592d4fa0ce06`.
The same application APK also passed the focused device-identities mode / 269
checks on API 26, including full catalogue loading, the canonical-name fixtures
and both appearances/text scales. Its initial scroll assertion positioned the
list at bottom padding, clipping 27 pixels of a row that fitted the viewport;
the harness now aligns the row itself. This changed test positioning only.
The API 26 rerun used instrumentation APK SHA-256
`06ccaec32bd43376a25cedb54246a0b47e0dcaf32050552a73427baebdff5399`.
Its artifacts are under `artifacts/global-catalog-api26-verified`. The other eight
modes were not run on API 26 for this change.

Ignored verification artifacts use `artifacts/global-catalog-final-*`;
`global-catalog-heap-check.log` records the host heap check.

Review corrections on 2026-11-14 remove two response-order dependencies. Up to
eight distinct model spellings remain available for qualification after the
manufacturer or catalogue loads; conflicting reports and overflow still suppress
recognition. A trusted PS5 console response can corroborate an earlier ordinary
model report, retaining its source even after display-observation eviction.
Both regressions failed against the preceding code before their fixes.

After those corrections, all 26 host suites / 29,914 checks, direct application
and instrumentation builds, and packaged permission/security audits passed.
API 26 passed device-identities / 300 checks. API 37 passed device-identities
(300), Recog (298), local metadata (49) and fresh presence (31): 678 checks total.
The app and test APK bytes installed on both isolated emulators were verified.
The other modes and API 36 were not rerun for these logic corrections.
Artifacts are under `artifacts/catalogue-review-fixes-*`.
Corrected development APK SHA-256:
`6d4269f39eb6e6f65ac223316b5d7a461b6bd1d1016fd0e8ac215a9959b604d7`.
Corrected instrumentation APK SHA-256:
`3212fc23f8af6b6287b0f7422b909a99923bcb687d55640de9714bad7245696e`.

Physical PS5 discovery, household model accuracy, TalkBack operation and phone
scan performance remain unverified. No phone was operated for this release.
The versioned 0.3.13 development APK passed 300 API 37 identity checks, and the
exact public-signed APK passed 21 in-place update/launch checks from 0.3.12.
All 116 isolated signing assertions and both package audits passed. See the
release verification notes above for hashes and scope. These changes do not
resolve the discovery limitations below.

## Offline model recognition (0.3.12)

The source now interprets 156 exact current mDNS device-info model codes using
a pinned offline Rapid7 Recog subset. The list and details label interpretations
as locally recognized, retain the reported values and expose rule/input
provenance. Matching categories select more specific Lucide icons and can be
searched. Missing manufacturer/MAC values remain unknown. Conflicting evidence
suppresses recognition; native cached callbacks cannot supply the input. No
network request, permission, persistent history or runtime dependency was added.

Review corrections keep descriptions conservative for 19 shared model codes,
retain conflicts for the entire scan despite display-observation eviction, and
test the DNS-SD first-duplicate-value rule through raw packets.

All 23 host suites / 19,574 checks, direct app/instrumentation builds and packaged
permission/security audits passed. The latest shared-identifier correction passed
the focused recognition mode / 276 checks on each of API 36 and 37, including
family-labelled details, reported codes, clipboard and narrow 200% text. Synthetic
corrected-family screenshots were inspected. Confirmed System UI ANR dialogs on
both emulators were closed before testing. The earlier six-mode matrices remain
recorded separately; the other five modes were not rerun for this table correction.
Physical model accuracy, coverage and scan performance remain unverified; no
phone install or release was performed. The existing discovery limitations below
remain open. See [RECOG.md](../RECOG.md) for selection policy, exact artifacts and
verification limits.

## Typography and IPv6 layout (0.3.11)

Native text styles now share scalable sizes and leading, with larger supporting text, medium/semibold emphasis and sentence-case detail labels. IPv6 remains below IPv4 and wraps between hexadecimal groups without inserted characters, ellipsis or automatic font shrinking. Full scoped addresses remain selectable and copy unchanged. This is a presentation change; discovery behavior and the known DNS-SD limitation remain unchanged.

## Scan efficiency update

Version 0.3.8 avoids redundant presence probes after a validated response in the same scan and defers device-list snapshots until the existing presentation queue needs them. Independent detail lookups, fresh-scan isolation, confirmation rules, retry budgets and discovery deadlines remain. These changes do not resolve the intermittent DNS-SD limitation below. Controlled benchmarks and physical-device timing comparisons must distinguish CPU/allocation reductions from total scan duration and device coverage.

## Local device metadata (0.3.12)

The source now adds Thread model/vendor TXT fields, targeted ESPHome/Shelly/WLED
discovery and bounded local Shelly/WLED/Spotify Connect/IPP metadata queries.
These changes retain fresh-response admission, source labels, unknown values and
the existing scan deadline. Protocol fields and limits are documented in
[LOCAL-METADATA.md](../LOCAL-METADATA.md). Synthetic protocol and emulator checks do
not establish compatibility or coverage on real devices.

Before the diagnostic extension, the build including the Gen1 short-ID correction
passed
20 host suites (14,131 checks), direct app/instrumentation builds, packaged
permission/security audits and the focused API 36 metadata suite (24 checks).
Earlier review testing covered API 26/36/37 and synthetic Android-bound metadata
exchanges; the metadata notes distinguish those results from the final correction.
An authorized Pixel 8 (API 37) received the matching-signed candidate in place;
installed bytes and preserved permission grants were verified. Its updated process
started empty without an automatic scan. Two fresh foreground scans accepted IPP
and Spotify metadata but remained Partial because some Spotify metadata was
unavailable. One also reproduced the existing native HTTP resolution failure.
Stop scan reached a terminal Partial state; full cancellation method diagnostics
were not captured. See the metadata notes for artifact identity, observed timings
and the exact limits. Keep the PR draft pending investigation of the unavailable
Spotify responses. Real model accuracy, unobserved device families, trusted IPPS
and physical lifecycle transitions remain unverified for this candidate.

The diagnostic extension classifies metadata transport, HTTP and protocol failures
and adds aggregate mDNS receive counters. It retains the original work limits and
fresh-response rules. It passed 20 host suites / 15,243 checks, direct app/instrumentation builds,
package audits and four API 36 suites / 148 checks. An independent Deep review
approved the change. Its validation is recorded separately from the earlier
phone candidate. A matching-signed diagnostic retest on the authorized Pixel 8
(API 37) verified update bytes, grants and empty startup. Two fresh scans took
about 36 seconds and remained Partial: Spotify recorded connection timeouts, with
one additional connection failure in the second scan, and both reached the IPv4
mDNS receive cap. Substantial non-response traffic consumed that receive budget.
Stop scan again reached terminal Partial. The cause of the connection failures
and the known intermittent native HTTP resolution limitation remain open. The
metadata notes record exact artifacts, interrupted helper attempts and coverage
limits; reporting changes do not resolve discovery failures.

## mDNS reliability pass (0.3.12)

The current branch separates raw receive traffic from full response decoding,
adds current-scan question/known-answer suppression and retains finite byte,
packet, record and time limits. A mixed-traffic regression reproduces the old
premature stop and passes with the revised receiver. All 21 host suites / 15,790
checks, direct builds, package audits and five API 36 suites / 159 checks passed.
A fresh independent Deep review approved the mDNS implementation. Two controlled
Pixel 8 (API 37) measurement scans avoided the receive cap; both direct mDNS
method reports were OK. They still ended Partial: Spotify connections failed,
and native HomeKit/HTTP resolution deadlines recurred in the second scan.
Extra bounded Spotify TCP retries did not recover the failing paths: IPv4
remained timed out and IPv6 was actively refused. A separate computer on the
direct LAN also found one currently advertised IPv4 Spotify port unavailable
through both 400 ms and 1,000 ms attempts; three others opened within 400 ms.
These aggregate results do not prove an identical failing peer across clients.
Longer connections have not demonstrated a workaround. An exact-name-filtered
API 37 discovery trial initially returned usable HomeKit/HTTP data, but extended
phone validation reproduced both failures through that API and its fallback.
The extra resolver path and tests were reverted; retained code matches `9df32e6`.
The mDNS receive-limit improvement remains, while native-resolution and Spotify
availability limitations remain open. Exact measurements, rejected experiment
boundaries and final-candidate checks are recorded in
[LOCAL-METADATA.md](../LOCAL-METADATA.md). The PR remains draft.

The earlier restored matching-signed candidate was verified on the authorized
Pixel 8.
Its source matches reviewed `9df32e6`; direct builds/audits passed, and its rebuilt
artifact passed two focused API 37 suites / 45 checks. The final fresh phone scan
took 37.75 seconds including UI observation: HomeKit, HTTP, direct mDNS and IPP were
OK, while one Spotify connection timeout kept it Partial. This successful native
sample does not erase the intermittent failures reproduced in the rejected trial.
Stop scan reached terminal Partial after 6.82 seconds including UI observation,
with complete method diagnostics captured. That candidate is historical; the
diagnostic build below supersedes it.
Private probes and the rejected native path are absent. No release was published.

## Native and wire diagnostics (implementation-stage record)

The next increment correlates terminal modern Android service-resolution failures
with captured current-scan records. Fixed counters distinguish no matching wire
observation, history without a retained graph, incomplete or complete current
graphs, and unavailable correlation. Cleanup also counts replies and complete
graphs arriving after native failure, retaining only bounded diagnostic completion
timestamps beyond identity expiry. A missing reply is not proof of stale cache.
These diagnostics do not change discovery APIs, retries, identity admission,
deadlines or Partial reporting.

It passed 22 host suites / 16,079 checks, direct app/instrumentation builds, package
audits, API 37 five suites / 141 checks and API 36 four suites / 110 checks. A minor
expiry issue found by independent Deep review was reproduced and corrected;
targeted verification confirmed the fix. The signed diagnostic candidate from
`e892814` is installed, with bytes/grants/empty startup verified. Three fully
captured phone scans took 37.27, 36.87 and 36.27 seconds including UI observation.
HomeKit, HTTP, direct mDNS and IPP were OK; Spotify connection failures kept all
three Partial. No terminal native failure occurred, so the real-phone wire-failure
classification and native root cause remain unverified. Stop reached Partial after
16.26 seconds with its complete report captured. Tooling interruptions were excluded:
the reader's false end-of-report shortcut was corrected, and read-only focus checks
identified the chat app owning input while Network Lens was visible. The foreground
guard remains unchanged. See the metadata notes for artifacts and exact limits.

## Per-endpoint metadata outcomes (implementation-stage record)

Device details now distinguish each endpoint's metadata success, refusal, routing
failure, timeout, protocol error, work limit and unfinished/stopped lookup. The
protocol, address family and attempted port remain attached to that endpoint.
Other current observations supplying its displayed model/manufacturer are shown
with their sources. Grouped addresses do not borrow these alternatives from one
another. This adds no requests, retries, cloud recognition or saved history.

The increment passed 22 host suites / 16,209 checks, direct app/instrumentation
builds, package permission/security audits, five API 37 modes / 987 checks and
four API 36 modes / 956 checks. Layout coverage includes both appearances,
320dp/411dp widths and normal/200% font settings. An initial layout assertion
counted trailing whitespace; native measurements confirmed visible text fit and
the corrected assertion passed. A System UI not responding dialog interrupted
the first API 36 reporting run; its rerun passed. A fresh independent Deep Kaz
review approved the complete increment with no substantive findings. The signed
candidate from `dbf7828` was updated in place on the authorized Pixel 8: installed
bytes, preserved grants and empty startup were verified. One fresh scan reached
Partial after 36.12 seconds including UI observation. After a foreground handoff,
read-only inspection captured five Spotify outcomes across all four filtered
groups: three IPv4 successes, one IPv4 connection timeout and one IPv6 refusal.
These match the aggregate five requests/three replies and the two failure
counters. Two endpoint panels showed current model/manufacturer alternatives.
An optional follow-up to attribute those alternatives specifically to the failed
outcomes stopped because the chat app held input focus. That attribution,
physical Stop on this candidate and its full method report remain unverified.
Existing native/Spotify limitations remain open; these diagnostics do not resolve
unavailable endpoints.

## Known discovery limitation

Repeated physical-device scans reproduced intermittent resolution deadlines for HomeKit (`_hap._tcp`) and HTTP (`_http._tcp`). Android registrations sometimes supplied no usable address update within both bounded attempts. Other discovery methods continued, and the scan correctly remained Partial.

Controlled diagnostic experiments have not established whether device behavior, packet delivery, Android resolver/cache behavior or another interaction causes the silence. A longer timeout and a replacement resolver library have not been demonstrated to solve it. Version 0.3.7 preserves full bounded errors, publishes coherent terminal diagnostics, names affected methods in Scan details and clarifies the unused IPv6-check wording. It does not suppress these failures.

## Previous release validation

The accepted 0.3.7 development APK passed 19 host suites (13,735 assertions), 30 focused Android suites across API 26/36/37 (1,375 checks), packaging/security audits and 20 final phone journey checks. Both final physical scans remained Partial for the two methods above. Passing reporting tests is not a claim that resolution succeeded.

These are historical results for the accepted build, not a fresh test claim for arbitrary future commits. Personal scan evidence and historical diagnostic tools are retained locally and are excluded from the public repository.

## Before production

Further discovery reliability work, real Thread-network validation and broader device testing remain. Google Play enrollment, listing/privacy declarations and a verified App Bundle pipeline are separate future work. GitHub development previews are intended to gather feedback; they do not establish production readiness.

## Version 0.3.8 preview verification

The production-signed APK passed signature, alignment and package audits, 40 installation/update checks across API 26/37 and a physical Pixel 8 journey. That phone journey recorded 17 migration checks and 86 unique UI/scan conditions, three Complete scans and one intentionally stopped Partial scan. Stronger, endpoint-verified navigation reruns covered both fully visible and partly clipped rows after two inconclusive coordinate-based attempts. No app fix or cause for those earlier results is claimed.

The signing identities were independently backed up and exercised through restored-key signatures. The source package includes upstream source/notices and public IEEE inputs whose regeneration matched the bundled vendor asset byte for byte. Exact artifact identities, historical test attempts and remaining limits are recorded in [VERIFICATION-0.3.8.md](../VERIFICATION-0.3.8.md). No household inventory or private signing material belongs in those public notes.

Final physical checks additionally covered Home cancellation, a full process restart, a verified Wi-Fi connection replacement and local-network permission denial/restoration. Recovery scans still reproduced the known HomeKit/HTTP resolution failure; passing lifecycle checks do not repair it.

## Release-signing preparation

The direct APK pipeline has an explicit `--release` path, separate production/app and Play-upload identities, pinned public fingerprints and a recovery-verification gate. Existing development signing and artifacts are preserved. See [RELEASE-SIGNING.md](../RELEASE-SIGNING.md). This source configuration alone does not claim a successful backup, signed binary, Android installation test, Play enrollment or public release; current verification belongs to its exact artifact/report. A verified App Bundle build and Play Console setup remain separate work.

## Repository migration verification (2026-10-29)

The prepared public source export passed all 19 host suites / 13,735 assertions and built both the app and Android instrumentation companion. Permission/security package audits passed. Its application ZIP payload matched the accepted 0.3.7 APK; signing was tested with a separate temporary development key. The existing accepted APK, source bundle and signing files were preserved. Production Kotlin and Android resources were unchanged. Household-derived fixture identifiers were replaced with synthetic values. No new Android runtime or physical-phone journey was run for this repository-only change.
