# Network Lens 0.3.25 verification

Prepared on 2026-11-18. Android development preview for home-network testing.

## Identity and changes

- Application ID `io.github.kazands.networklens`; version **0.3.25 / code 53**.
- Android 8.0+ (API 26); target API 37. Public signing identity is unchanged.
- Qualifying current SmartThings records identify **SmartThings Hub · Smart-home hub**.
  Useful friendly names remain beneath the title; technical identifiers stay in details.
- The rule requires a SmartThings service, a hub-shaped hostname and matching
  instance identifier. The service alone cannot classify TVs or appliances as hubs.
- Matter remains a separate capability. Exact generation, Zigbee/Z-Wave support,
  manufacturer and MAC are not invented from these records.
- Conflicting identity evidence remains constrained across snapshots and grouped
  IPv4/IPv6 addresses. Cached native callbacks cannot supply the identity.
- No new permissions, cloud recognition, retained inventory or background scans.
- See [SmartThings coverage](SMARTTHINGS.md) for the observed pattern and exclusions.

## Review and candidate checks

Deep kaz-code-review and a fresh independent reviewer approved all 17 changed/new
files without substantive findings. The independent full rerun passed all **46 host
suites**, including **40 SmartThings checks**, both offline Python diagnostic
modules (**7 tests**) and generated-data consistency checks. The initial restricted
run could not open a loopback fixture; the complete rerun with loopback access passed.

The candidate passed **1,040 Android checks on API 37**: `smartthings` (32) and
`device-identities` (1,008). After the final explanation-text change, the focused
32 checks passed again on the exact rebuilt APK. Installed APK hashes were verified.

## Versioned artifacts and checks

- Direct development, public-signed and instrumentation builds passed, including
  packaged permission/security audits.
- All **173 reviewed Kotlin application/test sources** are unchanged.
  All **48 payload entries except the manifest** match the reviewed candidate.
- All **49 non-signature payload entries** match between development and public APKs.
- Release-signing tests passed **116 assertions** using isolated test credentials.
- Development APK SHA-256: `0f91da2b57e1f21fdf1ef74565a74e5b4056d50aa9e5f05fac413c425c5b8db5`.
- Public APK SHA-256: `66f235d819005b0513851806a8f1823268f7e89d32e211c0c16bdc48132d4a15`.
- Instrumentation APK SHA-256: `caa41516f58461fa2a6b5d8228e2e5dd557b6e57653c6604896af4f46ed300fd`.

- The exact versioned development APK passed **549 Android checks on API 37**:
  `smartthings` (32), `app-updates` (502) and `scan-ownership` (15). Installed
  application/test hashes were verified.
- The exact public APK passed **23 in-place update/launch checks** from public
  0.3.24 on API 37. The pinned certificate, installed bytes, version, application
  UID and runtime permissions were verified. The app opened normally and waited
  for a user-started scan.

## Distribution and remaining checks

The release includes the signed APK, matching tagged source package, verification
notes and SHA256SUMS. The source manifest identifies the exact Git commit/tree,
per-file hashes and separately identified public regeneration inputs. Private
signing material, household data and local caches are excluded.

Physical SmartThings hub recognition remains unverified. The hostname convention
is an observed compatibility pattern, not a published generation guarantee; other
hub patterns remain unsupported until evidence is available. No phone was operated
for this release. Live in-app download/approval, physical TalkBack, other Android
versions and device-specific compatibility remain separate checks. No GitHub CI
was added; verification is local. This remains a development preview.
