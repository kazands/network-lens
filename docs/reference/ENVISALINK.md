# Envisalink recognition

## Current behaviour

The dedicated Envisalink TPI identity reader and **Check for Envisalink** action
have been removed. A user should not need to decide whether identifying an alarm
interface is worth interrupting another integration. The vendor documents one
concurrent TPI client; even a short receive-only connection can briefly occupy
that slot. This removes the check rather than promising it is harmless.

Normal scans have no dedicated TCP 4025 sweep, greeting reader or per-device
request path. Generic discovery can still confirm ports explicitly advertised
by a device; this change does not prohibit every possible connection to an
advertised port. Reported identity from other supported readers remains subject
to the usual evidence and conflict rules. Without such information, an
Envisalink endpoint can remain unnamed. No replacement automatic identification
method is claimed.

Release 0.3.33 contained the explicit per-device check. Its release verification
is historical and does not describe the removal in 0.3.34. Legacy evidence
vocabulary remains for interpretation tests; it exposes no socket transport.

## Sources and provenance

- [Vendor TPI documentation](https://forum.eyezon.com/viewtopic.php?t=301),
  [DSC TPI 1.09, June 9, 2024](https://forum.eyezon.com/download/file.php?id=379),
  sections 2.2 and 3.1: port, single-client limit, initial greeting and checksum.
- [Vendor local-access application note](https://www.eyez-on.com/EZMAIN/AccessingEnvisalinkApplicationNote(EZ).pdf):
  authentication and NetBIOS lookup.

Original code is GPL-3.0-only; no Fing/Nmap code or database is used. No router
DHCP/PTR identity, MAC-only model inference, login or alarm command is added.

## Regression checks

`EnvisalinkInfoTest` retains exact/invalid greeting vocabulary, family-only
catalogue interpretation, conflict and snapshot checks. The emulator-only
`envisalink` mode verifies that the dedicated scheduler, transport and manual
action are absent, a fresh responder receives no invented alarm identity, and
ordinary device details remain usable. It also runs within `device-identities`.

Use the direct commands in [BUILDING.md](../BUILDING.md). Emulator checks do not
establish physical-device compatibility.

### Original candidate verification — November 18, 2026

The results below predate the explicit per-device check. See the
[0.3.33 verification](../archive/VERIFICATION-0.3.33.md) for that earlier
scheduling change. [Current verification](../VERIFICATION-0.3.38.md) records
the removal and the new interface.

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
