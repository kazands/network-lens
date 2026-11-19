# Network Lens 0.3.30 verification

Prepared on 2026-11-19. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **58**.

## Reviewed changes

Supported Kidde WD-family advertisements now identify a **Kidde Smart Detector**
with a **Safety detector** subtitle and sensor icon. Matching requires current,
same-endpoint service, instance, hostname and model evidence. Exact retail models
and sensor capabilities remain unknown. See [changes](CHANGES.md),
[matching boundaries](reference/KIDDE.md) and [current limits](STATUS.md).

Independent Deep kaz-code-review identified two minor issues: duplicate model
keys lost during decoding, and a grouped proxy endpoint restoring an unqualified
HomeKit label. Both were fixed with regressions and independently confirmed;
the targeted follow-up approved the fixes with no remaining concrete findings.

Release preparation changed version metadata and documentation. All 50 APK
payload entries other than the manifest match the reviewed implementation
candidate; all 51 non-signature entries match between development and public builds.

## Passed checks

- All 52 host suites and 29 Python diagnostic tests passed on the implementation
  candidate, including 69 Kidde checks. The full host suite was not rerun solely
  for release version and documentation changes.
- Candidate API 37 checks passed: `kidde` 39, `device-identities` 1,277,
  `homekit` 69, `split-mdns` 24 and `recognition-report` 235: 1,644 assertions.
  These are candidate results, separate from the versioned checks below.
  [Candidate notes](reference/KIDDE.md#implementation-candidate-checks--2026-11-19)
  record the initial emulator and fixture failures and successful reruns.
- Versioned development, public-signed and instrumentation builds passed,
  including packaged permission/security audits and pinned signing-identity checks.
- The exact versioned development APK passed the API 37 matrix below in one run,
  with installed app and test hashes verified.
- The exact public APK passed 23 in-place upgrade and launch checks from public
  0.3.29: certificates, installed bytes, versions, UID, existing runtime grants,
  launch and no automatic scan. Only an isolated emulator was operated.
- Recognition coverage, repository hygiene, project documentation links and
  whitespace checks passed. No GitHub CI is configured or was added.

| Android mode | Assertions | Result |
| --- | ---: | --- |
| kidde | 39 | PASS |
| device-identities | 1277 | PASS |
| app-updates | 553 | PASS |

Total: **1,869 Android assertions across three modes**. The broader identity mode
also includes Kidde checks; this total is not a count of distinct test scenarios.

## Artifact identity

- Public APK SHA-256: `0db259abd719f0fa6944982e950e2cd303904ae859f62a985f56fac5e152dbb0`.
- Development APK SHA-256: `0d4f1034a27292e7b4df49ff569c476c858f600473752498dd036291f7ff5938`.
- Instrumentation APK SHA-256: `32cd4c71a5630eb0d705d3473f0067c3709a5e99be2cafde9327d9dfd12cfb7d`.
- Reviewed candidate SHA-256: `e689ff6c1c533e7338e5ae72a1ec3a1b780ab5101a20acc3d8c4d653c808500a`.

The public certificate remains pinned to
`5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
The source archive identifies the merged Git commit/tree, per-file hashes and
separately listed public regeneration inputs. Credentials, household data and
local caches are excluded. Asset checksums are supplied in `SHA256SUMS`.

## Limits and required physical checks

The updated APK still needs a fresh physical scan of a supported Kidde detector.
The fingerprint has limited physical validation and does not establish coverage
of every Kidde model. It does not identify the P4010ACSCO-WF retail SKU, establish
smoke/CO capabilities or prove working HomeKit compatibility. The advertised
identifier is not treated as a hardware MAC, and raw unknown fields stay unknown.

The reported Google Play Protect classification remains unresolved. Successful
emulator installation does not establish Google clearance or explain the warning.
No claim is made that this release removes it; keep Play Protect enabled.

No phone was operated. Real /22 timing, firmware compatibility, multicast
reliability, TalkBack, low-memory performance and API 26/36 were not retested.
Live in-app internet download and physical installation remain separate checks.
No new permission, network probe, alarm command, account access, background scan,
saved history or cloud recognition was introduced.
