# Review follow-up: implementation history

These changes are included in Network Lens 0.3.20. Version labels, hashes and
publication status below describe earlier candidates. See
[release verification](VERIFICATION-0.3.25.md) for the versioned release artifact.

This working-tree candidate follows published 0.3.19, retaining its version label
until release preparation. It is not the APK currently offered on GitHub. No CI
workflow is included, as requested.

## Changes

- EAP HTML comments are scanned linearly. Unterminated tails cannot authorize the
  identity POST; the technical details describe the credential-free request.
- Automatic large networks retain service discovery without allocating an
  oversized address sweep or silently substituting a /24. Method coverage remains
  explicitly incomplete when that sweep was omitted.
- One terminal owner closes sockets and coordinates protocol threads, executors,
  NSD cleanup and metadata plans. Late registration closes/rejects resources.
- SSDP, TP-Link, Omada and PlayStation transport adapters are extracted; SSDP has
  a pure channel engine. Metadata admission/plans and presentation snapshots have
  separate ownership boundaries. Remaining extraction is incremental.
- The local installer-exception and multicast-DNS TC fixes and their regressions
  are retained. TCP refusal/presence policy, original scope validation, transport
  security, source-labelled identity and RAM-only results remain intact.
- Redacted baseline assessment, safe physical measurement tooling, architecture
  documentation and synthetic Android performance measurements are included.

## Verification

The direct development build and packaged permission/security audits passed.
All 42 Kotlin host suites and both offline Python test modules (seven tests) passed.
The adversarial 16/32/64 KiB EAP parser probe rounded below 1 ms on the desktop
Java 21 runtime; its original 64 KiB case took approximately 2.65 seconds. This
desktop comparison is not an Android timing claim.

The first API 37 matrix retained its failures under ignored local evidence.
Two metadata fixtures still referenced the old private field after extraction;
they now use the new owner. The IPv6 fixture could receive a legitimate emulator
hostname before its synthetic response; it now re-arms its bounded exchange and
checks source-labelled admission rather than assuming presentation priority over
another valid hostname. The emulator also reported a System UI startup ANR before
the run, blocking window focus for the permission test. The runner now rejects
that unhealthy setup explicitly. The clean API 37 run passed all 21 selected modes, totaling 2,191 Android checks, with installed APK and instrumentation hashes verified. The Android adversarial EAP case measured approximately 1 ms. Synthetic index loading was 2,551 ms, heap delta 39,558,576 bytes, PSS after the workload 73,843 KiB, and the 256-endpoint snapshot median/max 61/171 ms. These are emulator observations, not phone performance or cold-start claims.

The initial API 26 run passed 13 of 14 modes. Its Cast fixture accepted only one HTTP request, which could be the independently scheduled group request; the identity request then timed out. Diagnostics confirmed the group path won that fixture response. The fixture now serves both identity and group paths independently, preserving privacy assertions and production deadlines. Its focused API 26 rerun passed 17 checks. The final API 26 matrix passed all 14 selected modes, totaling 1,119 checks. Its installed application hash matches the API 37 development APK. The final API 26 instrumentation SHA-256 is `2a8ab75a1dfb8b1a6f5b3ead14da51676c637d575c34007f00b4bb35109e9edd`.

Development APK SHA-256 before the explanation correction below:
`78fe83f7c5ce33133246d9a00d4c88302a8bf37414edef2b875990920870e2df`.
The 21-mode API 37 run used instrumentation SHA-256
`bfd3666b3aadc774509c9eed68e6a55847632b8c0fd35ed62fa1ac30d64bfa9a`.
The later Cast fixture adjustment changes instrumentation bytes, not application bytes.

Public-signed local candidate SHA-256 before the explanation correction below:
`63c99bce2b5a76128bbbf2329d821b8bf292043a4ba0227f7419395520dfb7e6`.
All 48 non-signature ZIP payload entries match the development APK. The signed
candidate is local and unpublished, still labelled 0.3.19. It has not yet been
installed on the phone.

## EAP explanation correction

The initial independent Deep review approved the broader changes with one minor finding:
the 165-character EAP request explanation exceeded the 160-character device-detail
limit and lost the end of its logging notice. The explanation is now 140 characters.
A regression verifies the actual stored detail retains the complete notice when
EAP work is queued at Stop or at the deadline. A production-class JVM check also
reproduced the original truncation and verified the corrected stored text.

The direct development, instrumentation and public-signed builds passed, including
the packaged application audits. The focused API 37 identity run passed all 698
checks, with installed application and instrumentation hashes verified. The full
matrices above precede this text correction; they were not rerun for it. These
results also precede the startup cancellation correction below.

EAP-corrected development APK SHA-256, before the startup correction:
`3b3754747668b40b4df6cb2a7a063bbe7a12bc52b571a1d53d984653cfd90a0c`.
Instrumentation SHA-256:
`7289231823545f06810c82f390bd49eba9581644d9a9137afcce2ae1fa7e2c1d`.
EAP-corrected public-signed candidate SHA-256, before the startup correction:
`db49bd2a90593362052385c34dfc6d43d0e33980f33f17a9972d5f52e3a3a105`.
All 48 non-signature ZIP payload entries match the corrected development APK.
This candidate remains unpublished and labelled 0.3.19; it has not been installed
on the phone.

## Startup cancellation correction before versioning

A subsequent independent Deep review found a startup/cancellation race: a scan
could re-enable admissions and acquire multicast reception after its terminal
owner had already closed. Startup and Stop now share a short lifecycle lock.
Native discovery rejects cancelled/closed owners, and multicast reception has an
owned, synchronized, idempotent release lease. Registration after cancellation
releases the acquired lock immediately. Final native cleanup remains a fallback;
its failures are reported instead of escaping finalization.

A production-class JVM reproduction with minimal Android service stand-ins
failed on the previous application classes with one retained multicast lock and
passed on the corrected classes. A latch-controlled Android regression pauses
the actual scan during startup, cancels it completely, then resumes it. It checks
that admissions stay closed and native discovery/multicast reception never start.
Separate emulator checks verify actual Android lock release, repeated cleanup
and rejection of stale native startup. A host latch test covers acquisition
before cancellation and registration afterward.

The direct development, instrumentation and public-signed builds passed, including
the packaged application audits. All 42 Kotlin host suites and seven offline
Python tests passed. Current-byte Android verification passed:

- API 37: 15 ownership checks plus 425 checks across permission, ICMP denial,
  scan efficiency, local metadata, fresh presence, session-only state, engine
  upgrade, resolver upgrade and service retries (440 checks total).
- API 26: 37 checks across ownership, engine upgrade and resolver upgrade.
  The first attempt ran the latter two before emulator Wi-Fi had connected;
  those failures remain in ignored local evidence. After confirming Wi-Fi was
  connected, all three modes passed without changing the checks or production code.

Both platform runs verified installed application and instrumentation hashes.
Current development APK SHA-256:
`88fae732e2a206a3f07029046cb2c66a500b44f0410128ec200fbabdc4e2162f`.
Current instrumentation SHA-256:
`f18dc4b44deeea3e56f5a27d6e147db0f071015898d3ec7a2131186a58b67537`.
Current public-signed local candidate SHA-256:
`e651cb2b4a94c5e153d631d318e3b77c9bdfbff5abeafcf3d3b86b82f3da44aa`.
All 48 non-signature ZIP payload entries match the current development APK.
The earlier full matrices and EAP-only focused run are historical evidence for
their recorded bytes. A subsequent fresh independent Deep review covered all
35 changed files and approved the corrected implementation with no substantive
findings. The reviewer verified these candidate hashes and inspected the existing
test reports; it did not claim another full suite execution. The coordinator
reran 88 lifecycle checks and the production-class cancellation reproduction.
This pre-versioning candidate remained unpublished and labelled 0.3.19; phone
testing was pending.

## Remaining boundaries

Real-phone recognition, cold startup, scan/cancellation timing, physical TalkBack
and battery results require separate authorized checks. The physical helper only
samples foreground aggregate memory/thread/frame metrics; it makes no cold-start
or accuracy claim. No physical-device result is inferred from emulator success.

These changes do not establish recovery of every unavailable Samsung, Apple,
Spotify, HomeKit or other identity endpoint. Missing evidence remains unknown.
See [architecture](SCAN-ARCHITECTURE.md), [baseline procedure](RECOGNITION-BASELINE.md)
and the published [0.3.19 verification](VERIFICATION-0.3.19.md).
