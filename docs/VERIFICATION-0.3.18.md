# Network Lens 0.3.18 verification

Prepared on 2026-11-16. Android development preview for physical testing.

## Identity and scope

- Application ID `io.github.kazands.networklens`; version 0.3.18 / code 46.
- Minimum API 26, target API 37. Public signing identity remains unchanged.
- Local TP-Link Kasa/Tapo plug and wall-switch discovery on UDP 9999/20002.
- Thirty-two reviewed models distinguish smart plugs, light switches and dimmers;
  regional model aliases combine while raw observations retain their sources.
- Bounded, read-only requests on the selected Wi-Fi/range. No authentication,
  pairing, control commands, account, cloud recognition or new permission.
- Current responses establish presence. Missing reported manufacturer/MAC remain
  unknown; shared plug/switch protocol labels do not establish a physical type.
- Existing in-app updates, session-only state and other recognition remain included.

## Implementation checks

- All 33 host suites passed, including 797 TP-Link checks.
- Before versioning, API 37 passed 714 checks across device identities, HomeKit,
  scan efficiency and session-only/cold-start behavior. One initial timing fixture
  timed out; the unchanged scan-efficiency fixture passed on the final run.
- Independent Deep review approved after correcting the self-only scan path so
  skipped TP-Link discovery cannot incorrectly make a scan partial. Regression
  coverage exercises that exact path.
- Tests cover valid/invalid packets, CRC and transaction matching, strict JSON,
  family/model admission, unknown models, duplicate and conflicting replies, range
  confinement, deadlines, cancellation, flood limits, snapshots, grouping, icons,
  source preservation and themed/scaled layouts.
- The production host discovery/parser identified an authorized physical HS103
  through both UDP formats. That was a desktop protocol check, not an Android
  scan. No household captures, device IDs or credentials are included in fixtures.

## Release artifacts

- Development/public builds and packaged permission/security audits passed.
- Development APK SHA-256: `23cb04976a481370df8e1bc0afa6b94ecb1d16235f4fd3222b92615a8549671c`.
- Public APK SHA-256: `29644034a4f457611fd2ad737f18d93fcad52a300e5f5e7232926bf5425052ef`.
- Public certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
- All 116 release-signing assertions passed using isolated generated credentials.
- All 43 application payload entries match between development/public APKs,
  excluding signing metadata.

- The exact versioned development APK and rebuilt instrumentation passed **714
  API 37 checks**: device identities 581, HomeKit 69, scan efficiency 22,
  session-only 39 and cold session 3. Installed APK hashes were verified.
- The exact public APK passed **23 update/launch checks**, updating public 0.3.17
  in place, retaining its UID and all three existing runtime permission grants,
  matching installed bytes/version/code, and opening ready for a user-started scan.
  No scan was started by the upgrade check. A stalled emulator boot was reset
  before the successful versioned matrix; it did not reach application tests.

## Published-download verification and limits

The release includes the APK, exact tagged source with public regeneration inputs,
checksums and these notes. Private signing material and household captures are
excluded. After publication, the real in-app 0.3.17-to-0.3.18 download/install
result is recorded in the release notes and a supplemental verification asset.

Physical dimmer compatibility, on-phone discovery with this build, API 26,
all-OEM behavior and physical accessibility remain unverified. Encrypted or
authenticated discovery extensions, UDP 20004, bulbs, cameras and hub-connected
accessories are outside this change. Unknown model codes are not assigned guessed
categories. See [TP-Link coverage](TP-LINK.md) and [remaining limits](STATUS.md).
This is not a claim of complete physical inventory or Google Play readiness.
