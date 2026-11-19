# Envisalink recognition

See [current release verification](../VERIFICATION-0.3.26.md) for exact artifact checks and physical-test limits.

Network Lens can identify the Envisalink family from the DSC Third Party
Interface (TPI) greeting. The list shows **Envisalink · Alarm interface**, with a
lock icon. The exact model, reported manufacturer and MAC remain unknown unless
separately reported. This does not distinguish EVL-3, EVL-4, 4MAX, DUO or a
compatible proxy. Recognition is an interpretation of a response, not proof of
hardware authenticity or alarm security.

## Wire behaviour and limits

For a confirmed current IPv4 endpoint, the bounded device-details worker makes
one independent connection to TCP 4025 even if the general port check already
found HTTP. There is no preliminary connection, retry, password, login, polling,
keepalive or alarm command. It reads at most eight bytes and accepts only
`5053CD\r\n`: command 505, password-request state 3, checksum CD, CRLF. A generic
`Login:` prompt, open port or truncated/invalid frame cannot identify the device.

The connection has a 250 ms connect timeout, 400 ms read timeout and 750 ms total
budget, further limited by the scan deadline. The socket is bound to the selected
Wi-Fi, tracked for cancellation and closed immediately after the check. Each
endpoint is checked at most once per scan, within the existing four-worker
DETAILS lane, bounded queue and 1,200-endpoint admission limit. Scope, current
presence, endpoint ownership and Stop are checked before accepting results.
Closed/unavailable ports are normal nonmatches; they do not add an unsuccessful
Envisalink row to every device. Connected services with no identifying greeting
retain a diagnostic without supplying an identity.

The vendor documents only one concurrent TPI client. If another integration is
connected, discovery may be refused. The scanner never attempts to displace it.
Even a brief receive-only connection temporarily occupies the slot, so a client
reconnecting at the same instant can be denied. The short connection and absence
of retries minimize this window; they do not eliminate it. Honeywell/UNO-style
`Login:` responses need additional evidence and are deliberately not identified.

The public HTTP interface can require authentication without exposing a useful
name. Manufacturer-documented NetBIOS name discovery is separate from the
node-status request already used by Network Lens; it is not added by this change.
No router DHCP/PTR identity, MAC-only model inference or cloud lookup is added.

## Sources and provenance

- [Vendor TPI documentation](https://forum.eyezon.com/viewtopic.php?t=301),
  [DSC TPI 1.09, June 9, 2024](https://forum.eyezon.com/download/file.php?id=379),
  sections 2.2 and 3.1: port, single-client limit, initial greeting and checksum.
- [Vendor local-access application note](https://www.eyez-on.com/EZMAIN/AccessingEnvisalinkApplicationNote(EZ).pdf):
  authentication and NetBIOS lookup.

The protocol reader is an original GPL-3.0-only implementation from the vendor
specification. No Fing/Nmap code or database is used. A receive-only host research
check confirmed the documented greeting; this is not a phone compatibility test.
Household addresses and captures are excluded from fixtures and documentation.

## Regression checks

`EnvisalinkInfoTest` covers exact/invalid greetings, fragmentation, zero outbound
application bytes, socket release, silent peers, cancellation, deadlines, closed
ports, permission errors, family-only catalogue interpretation and conflicts.
The emulator-only `envisalink` mode exercises source-labelled admission, physical
category/icon, unknown fields, snapshots, stale occupants, scope, Stop and Apple
fallback conflicts. It also runs within `device-identities`.

Use the direct commands in [BUILDING.md](../BUILDING.md). Physical Android Wi-Fi
recognition and behaviour alongside a connected alarm integration still require
separate verification. This feature does not read alarm status or control it.

### Candidate verification — November 18, 2026

- `scripts/test.sh`: all 45 host suites passed, including 38 Envisalink checks,
  plus the two offline Python diagnostic-tool modules.
- `scripts/build.sh` and `scripts/build-android-tests.sh`: passed. The local
  candidate retains version 0.3.23; it is not the published 0.3.23 APK.
- Isolated API 37 emulator: `envisalink` (28), `device-identities` (977),
  `recog` (284) and `scan-ownership` (15) passed: 1,304 Android checks.
  Installed application and test APK hashes were verified. The first emulator
  boot failed its System UI startup guard before tests ran; the matrix passed
  after a clean emulator restart.
- Changed/new source and documentation passed the repository path/credential
  checks; `git diff --check` passed. No household fixtures were added.
