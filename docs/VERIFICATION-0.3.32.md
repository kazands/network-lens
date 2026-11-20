# Network Lens 0.3.32 verification

Prepared on 2026-11-19. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **60**.

## Reviewed changes

Devices reporting manufacturer **ARRIS** and model **FibeTV** now use **ARRIS FibeTV**, with a **TV receiver** label and their useful room name retained. The profile identifies the receiver family without guessing a generation. Manufacturer qualification, original reported fields, conflict handling and compatible dual-stack observations remain intact.

Hydrawise and B-hyve router-name product suggestions have been removed. Local DNS names remain technical/report evidence, with default report masking; they cannot supply product identity, reported fields, primary type, presence or grouping. The new receiver test mode rejects physical targets before appearance initialization or legacy-preference migration.

A fresh independent Deep review covered all 20 implementation files and approved the changes with no substantive findings. All 54 host suites, generator/Python diagnostics and isolated application/instrumentation source compilation passed during that review. The startup regressions include 19 assertions covering rejection, read failure and accepted ordering. These are implementation-candidate results; the host suite was not rerun solely for release version/documentation changes.

Release preparation changes version metadata and documentation only.

## Versioned checks

- Versioned development, public-signed and instrumentation builds passed, with packaged permission/security audits and pinned signing checks.
- All 51 non-manifest APK payload entries match the reviewed implementation candidate. All 52 non-signature entries match between development and public APKs.
- The exact versioned development APK passed the API 37 matrix below; installed app and test hashes were verified.
- The exact public APK passed 23 in-place update and launch checks from public 0.3.31, including certificates, installed bytes, versions, UID, existing runtime grants and no automatic scan. Only an isolated emulator was operated.
- Recognition coverage, repository hygiene, current documentation links and whitespace checks passed. No CI is configured or was added.
- An initial emulator startup poll timed out before app tests began. The task-owned emulator was stopped; the restarted session completed the entire matrix in one run.

| Android mode | Assertions | Result |
| --- | ---: | --- |
| fibe-tv | 25 | PASS |
| network-names | 50 | PASS |
| device-identities | 1301 | PASS |
| recognition-report | 235 | PASS |
| app-updates | 553 | PASS |

Total: **2,164 Android assertions across five modes**. Some modes overlap; this is not a count of unique scenarios.

## Artifact identity

- Public APK SHA-256: `f5c451fea4d934ae345b3fdd4e39aa83aed151b7322ecef14e6141cc15604fe7`.
- Development APK SHA-256: `16063644376e7841abd57d7b563c428e26d84e11685e253b84c7e8deabe75993`.
- Instrumentation APK SHA-256: `37d3b3592408ec3732f9bb89550bba9d1e9c8e0a19c08b252f8c0154d460b67c`.
- Reviewed candidate APK SHA-256: `5cc75df3afb19ce90cb9b62b81b633025f94c93eecf3845d32c26f24e047dfc8`.

The public certificate remains pinned to `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
The source archive identifies the merged Git commit/tree and per-file hashes, with separately listed public regeneration inputs. Credentials, household data and local caches are excluded. Public asset checksums are supplied in `SHA256SUMS`.

## Limits and required physical checks

A fresh physical scan of an ARRIS/FibeTV receiver remains unverified for this APK. `FibeTV` does not establish a particular VIP model, storage capacity or generation. A friendly name or casting service alone cannot activate the ARRIS profile; missing or conflicting identity remains unknown.

There is no longer a Hydrawise/B-hyve identification claim based on router names. Devices that do not expose independently supported identity information may remain unnamed.

The reported Google Play Protect classification remains unresolved. Successful emulator installation does not establish Google clearance or explain the warning. Keep Play Protect enabled.

No phone was operated. Real /22 timing, multicast reliability, TalkBack, low-memory performance and API 26/36 were not retested. Live in-app internet download and physical installation remain separate checks. No permission, network probe, Bluetooth access, cloud recognition, account access, background scan, saved history or device-control command is added by this release.
