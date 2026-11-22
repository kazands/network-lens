# Network Lens 0.3.39 verification

Prepared on 2026-11-22. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **67**.

## Reviewed behavior

A current same-peer Google Cast advertisement with an exact supported VSSL model
can admit the existing read-only VSSL status query when AirPlay manufacturer
details were not received. The advertisement alone does not fill in a missing
manufacturer or identify hardware. A valid direct status reply supplies the
manufacturer, model and zone.

Bare or unknown models, service/room names, ambiguous model fields, detected
virtual Cast groups and conflicting manufacturer fields do not qualify.
Current-response, selected-Wi-Fi, IPv4 scope, endpoint ownership, lookup limits,
deadlines, retries, cancellation and RAM-only state remain in effect.

A fresh independent Deep review and coordinator verification approved the full
implementation diff with no substantive findings. Release preparation changes
version metadata and documentation only.

## Checks and artifact identity

All **55 host suites**, Python diagnostics and generated-data/coverage checks passed during release preparation. The development, public-signed and instrumentation direct builds passed. Packaged permission and security audits passed for both app APKs.

Signing preflight verified the pinned public identity and existing recovery receipt. The isolated signing harness passed **116 assertions** using generated fixture credentials. No signing identity was replaced.

All **107 non-manifest payload entries** match the reviewed implementation candidate; only the version manifest changed. All **108 non-signature payload entries** match between the development and public APKs.

## Exact versioned Android checks

Guarded isolated emulators verified their identity and installed app/test APK hashes. The exact 0.3.39 development APK passed the focused `audio-zones` mode on both supported test platforms:

| Mode | API 26 | API 37 |
| --- | ---: | ---: |
| `audio-zones` | 78 | 73 |
| **Total** | **78** | **73** |

These **151 checks** exercise Cast-only VSSL status admission, unknown manufacturer before a valid reply, identity application, later AirPlay deduplication, current-presence rejection, stale/replaced endpoints, Stop, related audio readers and Compose audio disclosure/copy behavior. They use synthetic devices and do not measure physical discovery accuracy. The broader interface matrix recorded for 0.3.38 was not repeated.

## Public signed update

The exact public-signed APK passed **23 update/launch checks** from public 0.3.38 on the isolated API 37 emulator. Both certificates and installed APK bytes were verified. The update retained the application UID and all three existing local-network/location runtime grants. Launch waited for a user-started scan.

## Artifact identity

- Public APK SHA-256: `64852127a703a8596e4c458f91eacad180e6d05a63f69c1dc21f0ca940b5fef7`.
- Development APK SHA-256: `5da38f4dd94e6f3e49a859d811a77a481a1399548dba4a76db3f4443561146c4`.
- Instrumentation SHA-256: `cccfbb196e52faf222a51fde611c5dfbf5d6fb0dcdaaf8630615ab3f2362a65e`.
- Reviewed implementation candidate SHA-256: `1db9e5b0c164369fdb85a70420be6da237d5ba6d55f5170e7e69dcd53fabe5f2`.
- Public certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

## Verification limits

The investigation inspected the failing result on an authorized phone and accepted
current advertisements and a status response using the app's shared parsers on
the host. These separate observations do not establish a fresh end-to-end scan
with the updated APK on that phone. No household replies were committed as test
fixtures. Firmware coverage and household discovery accuracy remain unmeasured.

The public update check uses an isolated emulator and `adb install -r`, not live
GitHub in-app installation. ARM64 performance, low-memory behavior, TalkBack,
OEM keyboard/capture behavior and the optional Wi-Fi-name permission flow remain
outside these focused checks. IPv6-only devices and routed Thread endpoints
remain outside discovery scope. The earlier Play Protect classification remains
unresolved; signing and emulator installation do not establish Google clearance.

The matching source package identifies the merged commit/tree and includes
required public regeneration inputs, per-file hashes and original notices.
Credentials, local caches and household evidence are excluded. Published assets
include SHA256SUMS.

[Build instructions](BUILDING.md) · [Previous release verification](archive/VERIFICATION-0.3.38.md)
