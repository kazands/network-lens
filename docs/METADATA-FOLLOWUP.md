# Metadata and permission follow-up — 2026-11-17

Historical implementation/candidate evidence for functionality included in
**0.3.19**. Candidate version labels, hashes and publication status below describe
those earlier checks. See [final release verification](VERIFICATION-0.3.19.md).

This is an unpublished follow-up to [pre-PR verification](PRE-PR-VERIFICATION.md).
It changes recovery and evidence handling; it does not claim universal device
compatibility or that every hardware warning has disappeared.

## Investigation and changes

- Current read-only checks received valid VSSL status frames from six responding
  zones. The existing parser accepted all six in memory. Current Spotify requests
  also returned successful protocol result codes. No household replies were saved
  as test fixtures. These results do not establish the cause of an earlier phone
  transport failure or prove that IPv6 and IPv4 expose identical services.
- Simple local metadata and VSSL/Sonos queries now allow one fresh-connection retry
  for transient transport failure. Both attempts share the original two-second
  operation deadline and selected Wi-Fi binding. Invalid metadata, HTTP errors,
  routing failures, refusal, cancellation and TLS failures are not retried.
  Admission remains per endpoint/protocol, with at most two transport attempts.
- A validated Apple QueryType response is retained when the subsequent ProductType
  response closes, is malformed, is oversized or otherwise fails. The endpoint
  shows a verified Apple service and the model failure; it receives no invented
  model or manufacturer. Cancellation and expired work still reject publication.
- Cast receivers can return valid Eureka identity JSON while omitting the requested
  multizone data. Such responses now record optional information not reported,
  rather than invalid metadata or an empty roster. The scan retains a limitation
  and group membership remains unknown. Malformed/unrelated JSON still fails.
  Actual HTTP replies observed here omitted multizone even when a capability flag
  advertised it; alternate detailed selectors did not recover it.
- The permission test waits for a visible, enabled scan control in a focused window
  and injects actual touchscreen events. Previously it called the view's click
  handler directly, which could request native permission UI during a focus
  transition. The old failure log included dropped focus reports. This is a
  supported test-harness correction; it does not establish a platform root cause.

## Reference and scope

[PyChromecast's read-only discovery implementation](https://github.com/home-assistant-libs/pychromecast/blob/master/pychromecast/dial.py)
checks multizone capability separately from device identity and handles HTTP/TLS
variants. No upstream source was incorporated by this follow-up. Network Lens
has not added certificate-validation bypasses or playback/control commands.

## Verification

The full host run passed all 39 suites, including 76 Apple device-info checks,
2,089 local-metadata checks and 68 broader-audio checks. Regression cases cover
retry bounds, non-retriable failures, cancellation/deadline exclusion, Apple
identity retention after failed model reads, and unreported versus malformed Cast
group metadata.

The focused permission correction passed four permission journeys (21 checks each)
with performance and lifecycle modes interleaved: 135 total checks. This run used
the corrected input helper before the final production rebuild; final-build
Android regression results are recorded below when completed.

A fresh independent kaz-code-review Deep review approved the metadata/permission
slice with no substantive findings. The reviewer inspected surrounding callers,
network/scope/cancellation guards and regression tests, and ran focused host
checks. It did not independently operate Android or physical devices. The earlier
broader change reviews remain separate evidence.

A new physical-phone scan against this follow-up has not been performed. The
previous Pixel installation remains the earlier candidate. Persistent hardware
limitations remain visible and require current-device verification before claiming
that the four original warnings have been eliminated. No PR or release has been
created by this follow-up.

## Final Android 17 candidate regression

All 13 selected modes passed: **1,279 checks** across device-identities,
local-metadata, audio-zones, failure-reporting, scan-details, service-retries,
performance, lifecycle, icmp-denied and four permission journeys. Each permission
journey passed 21 checks; all asserted native denial, grant and user-requested
scan completion. No retry of a failed mode was needed in this final sequence.
Installed application and instrumentation hashes were verified by the runner.
This does not prove the intermittent platform behavior can never recur.

Application SHA-256: `cfb6831d73d696deef97a46827f53923720e2c4a9fdbc2c097ad7338d1bca04c`.
Instrumentation SHA-256: `a082b86acc452c0a8d2fad98b0c549acd0cbde97d28edf268bccb902cee00f0e`.

The Android 8 follow-up was interrupted before completion and is not claimed as
a passing run of this metadata follow-up. Earlier Android 8 verification applies
to the preceding candidate only.
