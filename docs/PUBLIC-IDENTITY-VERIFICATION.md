# Public identity implementation verification

Implementation candidates checked on 20 November 2026, against the working branch `codex/public-identity-readers`, based on main commit `7a3fcc84ec58a6c3aa8262e41cb68ef629133d43`.

The development candidates below were labelled **0.3.34**, distinct from the published 0.3.34 APK. Their hashes and checks are historical implementation evidence. See [0.3.35 release verification](archive/VERIFICATION-0.3.35.md) for the versioned release artifacts.

## Final implementation review

A fresh independent Deep kaz-code-review covered all 35 pending implementation,
test and documentation files after the corrections below. Verdict: **Approve**,
with no substantive findings. The coordinator reran all 55 host suites and all
29 Python public-trial tests successfully. Repository guardrails, syntax checks
and diff checks also passed. No source changes were made during that review;
versioned release preparation and its exact artifact checks are recorded
separately in the release verification linked above.

## Host trial certificate selection correction

The follow-up full Deep review found two Minor issues in the optional Linux
trial. SAN selection now reads bounded, typed DNS/IP entries directly from the
certificate's DER encoding after OpenSSL accepts it, instead of searching SAN
display text. A URI/email containing `DNS:`, `IP Address:` or delimiter-like text
cannot become a selected DNS/IP name. Invalid or over-limit alternative names
produce a fixed notice. Label availability now requires at least one actually
retained CN, organisation or DNS/IP name; an observation without selected names
remains counted separately and contributes zero to availability.

All **29 Python public-trial tests** passed, including actual OpenSSL extraction
from synthetic certificates with URI/email marker text, positive DNS/IPv4/IPv6
names, and an empty-label certificate passed through the aggregate reporting
path. Fixture keys are generated in OpenSSL memory and discarded to `/dev/null`;
public certificates remain in RAM. Structural tests cover truncated and invalid
DER, duplicate SAN extensions, critical SANs, name/value limits and 1,000 bounded
malformed inputs. Python syntax checks, repository guardrails and diff checks
also passed.

These changes affect only the host trial, its tests and documentation. The app
APK and prior Android verification below are unchanged. No new APK build,
emulator run, physical scan or release was performed for these two corrections.
That checkpoint did not include another independent review; the later final
implementation review is recorded above.

## Certificate subject parsing correction

The latest review identified overlapping alternatives in the certificate-subject
regular expressions. Escaped input could spend seconds in synchronous matching,
outside the socket deadline's control. Both the app and Linux trial now select
CN/organisation attributes with a bounded forward parser of canonical RFC2253
output. Escaped separators and UTF-8 hex escapes are handled explicitly. Whole
subjects above 2,048 UTF-16 code units are rejected; selected values above 160
are omitted, as are duplicate or unsupported selected attributes. Fixed notices
explain omissions without retaining the original subject.

The direct app build and packaged permission/security audits passed. Current
development APK SHA-256:
`c4e823328cef518f6fabccc9ca922d6b04151d2dceb734d6b9264e45a10eb085`.
The test-APK build also passed; instrumentation SHA-256:
`43d94f7ccb5c63591bbc3e97be45893b3e1ceb96aa7018d580d762634d395fb7`.

All **55 host suites** and **25 Python public-trial tests** passed. The final
focused Kotlin rerun, including two Unicode-boundary checks added during the
full run's compilation, passed **2,080 assertions**. Cases include escaped
commas/pluses/backslashes, hex-encoded UTF-8, multi-valued RDNs, duplicate CNs,
malformed encodings and exact length boundaries. Actual X.509 extraction of
overlong escaped CN/organisation values runs in a child JVM with an eight-second
process deadline; the Python integration case has a five-second process
deadline. Synthetic certificates and keys are generated in RAM only.

Both guarded emulators rejected physical targets, verified the installed app
and test hashes above, and passed the following focused modes:

| Android API | Public readers and certificate regression | Metadata | Scan ownership | Compose reports |
| --- | ---: | ---: | ---: | ---: |
| 26 | 97 | 61 | 21 | 157 |
| 37 | 95 | 61 | 21 | 157 |

Total: **670 successful Android assertions**. Each API directly extracted fields
from a synthetic X.509 certificate containing overlong escaped CN and
organisation values, with a two-second worker deadline. The normal loopback
certificate observer still rejected its handshake, and ordinary HTTPS still
rejected the self-signed fixture. No problematic certificate was served over
the network. Both task-owned emulators were stopped after verification.

Repository guardrails for staged and changed/new files, Python syntax checks
and `git diff --check` passed. Physical compatibility, firmware-specific
recognition benefit and performance remain unverified.

The earlier candidate hashes and results below are historical, not additional
checks of this exact APK. No new independent review, release or phone
installation is claimed for this correction.

## Shared-root ownership correction

The follow-up review found that generic port-80 admission could precede the equipment reader and schedule a second root fetch against the same journal entry. Public admission now prepares the equipment reservation first, and equipment admission/journal reservation share the endpoint lock. A pre-existing root reservation prevents a duplicate equipment worker; a worker must also acquire the journal start before reading. The existing EAP model lookup is retained.

Updated development APK SHA-256: `d757ff2f39f918ffe3b549a937093ff3634cffbcc54b2a3d7d43ab256da89422`. Instrumentation SHA-256: `6b80cb892033f884b1590c068fb7607e271e581626c9b5187053da6c8f89fbfb`.

The direct app build, APK permission/security audits, test-APK build and all **55 host suites** passed again. Both guarded emulators verified installed hashes and passed these focused modes:

| Android API | Public readers and shared-root regression | Metadata | Scan ownership |
| --- | ---: | ---: | ---: |
| 26 | 96 | 61 | 21 |
| 37 | 94 | 61 | 21 |

Total: **354 successful Android assertions** on this updated candidate. Regressions cover generic-first and equipment-first admission, preservation of an already-started root's timing, concurrent observations, metadata queue rejection, public fallback when equipment admission is exhausted, diagnostic suppression notices and cancellation. Both reader lanes are blocked in the scheduling fixtures before any network operation; the transport fixtures use loopback only.

These are focused checks for the correction. The broader original results below apply to the earlier candidate hash; physical compatibility, performance and recall remain unverified. No PR, release or phone installation was performed.

## Initial candidate results

## Build and host checks

- `bash scripts/test.sh`: all **55 host suites** passed, including **2,052** public-reader/timing/transport assertions and **21** Python public-trial tests.
- `bash scripts/build.sh`: passed, including packaged permission and security audits.
- `bash scripts/build-android-tests.sh`: passed.
- Recognition-policy coverage, staged repository guardrails, working-tree guardrails for changed/new files, and `git diff --check`: passed. Guardrails do not guarantee absence of private data; the changed fixtures were also inspected for synthetic-only content.

Initial development APK SHA-256: `5f963067db1b16827b4830213a1e7bc3f9d4bb488570ca26d7966ad2d41b032f`.

## Android checks

Both runners rejected physical devices and verified installed app/test hashes. The same app APK was used throughout. Below are the successful final results per mode; assertion counts are not product support counts or accuracy measurements.

| Android API | Public readers | Metadata | Device identities | Scan ownership | Compose reports | IPv6 experiment guards/path |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 26 | 41 | 61 | 1120 | 21 | 157 | 6 |
| 37 | 39 | 61 | 1088 | 21 | 157 | 7 |

Total: **2,779 successful assertions** across these twelve mode results.

Test APKs by batch:

- API 26, initial batch: `54a4b519cffe219e9d9a8878273f29e787cecfef9c6e18a144c97811f277f52b`. Device identity results are superseded by the rerun.
- API 26, corrected identity/IPv6 batch: `17c2309ccca367630263803c091bc47e794372b0e8bc06f8aa1275047476ecc0`.
- API 37, initial batch: `54a4b519cffe219e9d9a8878273f29e787cecfef9c6e18a144c97811f277f52b`. Device identity results are superseded by the rerun.
- API 37, corrected identity/IPv6 batch: `17c2309ccca367630263803c091bc47e794372b0e8bc06f8aa1275047476ecc0`.

The first ownership fixture omitted its synthetic prefix and was corrected before the recorded public-reader passes. The broad Android 8 identity check also exposed a test assumption: an IP substring search can match several devices, so a narrow lazy list need not have composed the target row. The corrected test brings that actual row into view before asserting its unchanged product/family title. The rerun passed on both APIs; no app-list behaviour was weakened or changed for this correction.

The native public-reader checks exercised actual Compose disclosure/copy, journal privacy, ownership loss, late model arrival, terminal-state protection, loopback SSH and TLS. A separate certificate observer extracted a synthetic self-signed name and rejected the handshake. Ordinary HTTPS still rejected the same untrusted certificate on both APIs.

## IPv6 experiment outcome

API 37 sent a selected-Wi-Fi `ff02::1` datagram echo, received one packet and found **zero correlated non-self peers**. API 26 was denied during socket binding (`EACCES`, errno 13). Its passing assertions cover guard/correlation behaviour, not successful multicast transport. This is emulator feasibility evidence only; production multicast discovery remains disabled. No MAC address derivation was added.

## Independent review

A fresh, clean-context reviewer completed a full Deep kaz-code-review of the initial 32-file change. Verdict: **Approve with minor changes**, with no Critical/Major findings. Both verified findings were corrected: repeated ignored cookie headers no longer discard selected clues, and a UTF-8 character split at a deliberate prefix limit no longer discards earlier complete clues. Regression tests also verify that malformed interior UTF-8 and ambiguous selected/framing headers remain rejected.

Subsequent test-fixture corrections and the explicit root-prefix/IPv6 outcome notices were checked by the implementing agent and the regression runs above. They were not represented as a second independent full review.

## Limits and next validation

No household network trial or physical firmware/performance/recall comparison was performed. Historical troubleshooting reports did not establish that metadata caps needed increasing; this change adds measurements instead. Public titles, certificate names and SSH banners remain unverified service clues, never hardware identity or grouping evidence. Corroborated software families do not establish a physical device type.

Credential-based SNMP remains a follow-up. The next compatibility step is a fresh, explicitly authorised physical baseline and a comparison using this exact candidate, keeping household evidence out of Git. No CI, new permission, cloud recognition or persistent inventory was added.
