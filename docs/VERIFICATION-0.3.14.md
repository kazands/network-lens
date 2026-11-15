# Network Lens 0.3.14 verification

Verified locally on 2026-11-15 for a focused device-category correction release.
The broader identification and discovery work remains separate.

## Identity and artifacts

- Application ID: `io.github.kazands.networklens`.
- Version: 0.3.14 / code 42; minimum API 26, target API 37.
- Development APK SHA-256: `d9177f7bcec7cd1431a60c6aa07d4a8c03a533624bf0d0078e40f2ee4e28b35e`.
- Public APK SHA-256: `10dd83643b21e57ebfb2fada4276141cb5f99c815ce582297294199c9dbe57bb`.
- Public signing certificate SHA-256:
  `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

Both direct versioned builds passed signature verification and packaged
permission/security audits. Development/public APKs contain the same 40
application ZIP entries after excluding signature metadata. Compared with the
initial 344-check feature APK, only the manifest changed for the version increment.
The versioned APK was also tested separately below. The pinned release key and
recovery receipt passed preflight; no signing identity was replaced or regenerated.

## Changes

- Exact reported `ecobee` manufacturer plus `Smart_Thermostat_Premium`,
  `Smart Thermostat Premium` or `ecobee Smart Thermostat Premium` model selects
  **ecobee Smart Thermostat Premium**, the **Thermostat** category and thermostat
  icon. A friendly name or audio advertisement alone cannot authorize this profile.
- Sony's exact reported `sony_tv` brand spelling compares as Sony for catalogue
  recognition. A PS5 with corroborating model data displays **Sony PlayStation 5**
  and **Game console**. Raw reported manufacturer/model values remain unchanged.
- Unmatched audio capability displays **Audio service**, avoiding a claim about
  the device's primary purpose. Supported product categories retain precedence.

The bundled global catalogue remains at 256,338 qualified aliases, 86,540
product/family titles and 5,263 brand keys, plus the pinned Recog subset and
reviewed profiles. No discovery traffic, deadlines, permissions, grouping rules,
persistent state, online lookup or catalogue updater is added by this patch.
Missing and conflicting identity values still fail closed.

## Checks

The initial feature build passed all 26 host suites / 29,949 checks and 344
Android identity checks on API 37. The Android cases cover current-source
recognition, raw fields, conflicts, independent copies, dual-stack grouping,
titles, category icons, search, copy, Back and text wrapping at 320dp/411dp,
normal/200% text and both appearances. Fixtures are synthetic and cannot scan a
household network. The first search assertion counted address-map entries;
it was corrected to count distinct device rows for a dual-stack group.

- A fresh independent Deep code review covered the complete changeset and
  returned **Approve**, with no substantive findings. The reviewer independently
  compiled and passed 148 catalogue/presentation checks and 94 global-index checks,
  plus syntax, version wiring, documentation-link and whitespace checks.
- The versioned development/public builds and instrumentation build passed,
  including generated-table, signature, permission and security configuration checks.
- All 116 signing integration assertions passed with isolated generated credentials.
- The exact versioned development APK passed all 344 device-identities checks on
  an isolated API 37 emulator; installed app/test APK hashes were verified.
- The exact public-signed APK passed 23 update/launch checks on API 37. The current
  public 0.3.13 APK was installed first; 0.3.14 updated it without uninstalling,
  retaining its UID and all three established runtime permission grants. Installed
  bytes, pinned certificates, version/code, foreground branding and waiting for a
  user-started scan were verified. No scan was started by the update check.

The 26 host suites were run against the feature code before the release version
increment; they were not repeated for version/link metadata alone. Application
payload equality and the versioned Android run verify that boundary. API 26/36
were not rerun for this patch. Older checks remain in
[0.3.13 verification](VERIFICATION-0.3.13.md).

## Source and limits

Release assets include the public-signed APK, corresponding committed project
source, `SHA256SUMS` and these notes. The source archive's `RELEASE-SOURCE.json`
records its commit/tree and per-file hashes. Source provenance, upstream notices,
public IEEE regeneration inputs and Kotlin standard-library sources are retained.
Private signing files, recovery records, local tools and household data are excluded.

The corrected APK has not been validated on the user's physical devices. This
patch improves the confirmed model/category cases; it does not identify endpoints
that supply no useful identity evidence or resolve existing HomeKit/HTTP/Spotify
lookup limitations. Partial scans remain Partial. Physical TalkBack, phone scan
performance, broader compatibility and overall household recognition accuracy
remain unverified. See [status](STATUS.md) and [device identities](DEVICE-IDENTITIES.md).
