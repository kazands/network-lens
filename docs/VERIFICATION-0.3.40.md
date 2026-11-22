# Network Lens 0.3.40 verification

Prepared on 2026-11-22. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **68**.

## Reviewed behavior

Unanswered-address recovery and unidentified current devices receive one bounded
additional pass over TCP 8012, 10101 and 11095. Presence still requires two new
connections at least 200 ms apart. These ports supply no camera, manufacturer,
model or grouping assertion. Current devices retain confirmed ports during the
final enrichment drain; candidates cannot enter after admissions close.

Each additional pass has a 1,800 ms ceiling inside the scan deadline, at most two
160 ms connects per port, and a 1,024-address admission limit. The normal /24
scan deadline remains 55 seconds. Deadline omissions remain explicit.
Unanswered ICMP recovery waits up to two 600 ms attempts with a 120 ms gap;
identifier, sequence and nonce validation remain required.

Troubleshooting retains bounded, scan-local TCP confirmation outcomes and Apple
transport stages, fixed failure labels, timings and byte counts. Names/addresses
remain opt-in in exported reports. Exception messages and raw packet payloads
are not retained. These diagnostic changes do not identify an Apple device from
an open port or change Apple timeouts/retries.

Fresh independent Deep Kaz review found one Minor enrichment-drain issue. It was
fixed and independently reproduced as resolved, including current/candidate/absent
boundaries. The focused follow-up approved the changes with no outstanding
findings. No Critical or Major findings were reported.

## Host, build and artifact checks

All **56 host suites**, **42 Python tests**, and generated-data/recognition-coverage
checks passed. Focused independent reruns of additional TCP, ICMP, TCP confirmation
and troubleshooting suites also passed. The direct development, instrumentation
and public release builds passed; packaged permission and security audits passed
for both app APKs. Signing preflight verified the pinned identity and existing
recovery receipt. No key was replaced.

All **108 non-signature payload entries** match between the development
and public APKs. The installed app and test APK hashes were verified on both
isolated emulators before running the following exact-version checks.

## Android checks

| Mode | API 26 | API 37 |
| --- | ---: | ---: |
| `additional-tcp` | 79 | 79 |
| `scan-efficiency` | 27 | 27 |
| `device-identities` | 1109 | 1084 |
| `compose-reports` | 158 | 158 |
| `ipv4-scope` | 47 | 64 |
| `scan-ownership` | 21 | 21 |
| `fresh-presence` | 33 | 33 |
| `session-only` | 36 | 35 |
| `session-cold` | 5 | 5 |
| `app-updates` | 405 | 405 |
| **Total** | **1920** | **1911** |

All selected modes passed on the final APK. An initial run failed the existing
presentation fixture's three-second setup latch while catalogue loading occupied
the same executor. The fixture now completes catalogue setup first and stops the
real network watcher before supplying synthetic network state. Its scheduling,
batching, reset and stale-callback assertions were preserved. Both full selected
matrices were rerun after this correction and the enrichment fix.

These are synthetic protocol, lifecycle, report and UI checks. They cover
additional-only responders, silent/transient connections, cancellation, expiry,
scope, final enrichment, one-second delayed echo, Apple transport export,
IPv4-only admission, RAM-only state and update behavior. Counts describe
assertions, not recognized products or physical discovery accuracy.

## Public signed update

The exact public APK passed **23 update/launch checks** from public 0.3.39 on
an isolated API 37 emulator. Both certificates and installed APK bytes were
verified. The in-place update retained the application UID and all three existing
local-network/location runtime grants. Launch waited for a user-started scan.

## Artifact identity

- Public APK SHA-256: `e0233cabca4e527684732dbe259f934f4bfc60bb03f766f17390ac4919097ec0`.
- Development APK SHA-256: `5df892202a1d96a2d5f1ffbc7027daccc86369d327e955367820368ff1d14a18`.
- Instrumentation SHA-256: `b69662e52fef12e41bf519f393d70dfdf53902e362f121bf6a00433bfad89439`.
- Public certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

## Verification limits

The tester's exact Nest models/addresses are unknown. No physical Nest camera
scan or fresh end-to-end check of the reported Apple devices was performed with
this release. These reports remain unresolved until physical testing. Shared
ports cannot establish a camera identity; no dedicated Nest/Weave identity reader
is included. No household observations were committed as fixtures.

No full household timing benchmark was performed. Fixed budgets and synthetic
timing checks do not establish completeness on a busy network. Devices that send
no accepted current response can still be absent, and a listed device can remain
unnamed. A Partial scan is not complete.

The public upgrade uses `adb install -r` on an emulator, not live GitHub in-app
installation. ARM64 performance, low-memory behavior, TalkBack, OEM keyboard and
capture behavior, and the optional Wi-Fi-name permission flow remain outside
these focused checks. IPv6-only devices and routed Thread endpoints remain
outside discovery scope. The earlier Play Protect classification remains
unresolved; signing and emulator installation do not establish Google clearance.

The matching source package identifies the merged commit/tree and includes
required public regeneration inputs, per-file hashes and original notices.
Credentials, local caches and household evidence are excluded. Published assets
include SHA256SUMS.

[Build instructions](BUILDING.md) · [Previous release verification](archive/VERIFICATION-0.3.39.md)
