# Recognition evidence fixes — candidate verification

Verified on 2026-11-19 against the current working changes after commit
`06ee1951b2847b4c3d0ebf74aa4d5fa6058f1c74`. This record describes the unpublished development
candidate still labelled 0.3.27 / build 55. It is **not** the public 0.3.27 APK.
No GitHub release or phone installation was performed.

## Scope

- 179 unambiguous released iPhone/iPad board aliases from the retained AppleDB
  source, with canonical compatibility across services, addresses and copies.
- Compatible Brother printer naming and a narrow Google UPnP/Cast alias rule;
  retained contradictory observations still suppress product recognition.
- Reviewed printer, Echo Dot speaker-family and KP400 plug classifications.
- Apple conflict reporting requires actual recognised Apple model evidence.
- Optional unavailable Cast information is a completed check with a diagnostic
  count; failures and deadlines remain failures or unfinished work.
- Bounded troubleshooting retention prioritises hardware identity and lookup
  outcomes, counts rejected/displaced events, and preserves snapshot accounting.

## Passed checks

- All 50 host suites and 29 Python tests through `scripts/test.sh`.
- Direct development APK and Android instrumentation builds, including packaged
  permission and security audits. Existing Java-target/deprecation warnings remain.
- A fresh independent Deep code review approved the implementation with no
  substantive findings. It independently ran seven focused host suites (5,111
  checks), four generator tests, generated-data consistency, all 179 board ownership
  and canonical-target checks, recognition coverage policy and whitespace checks.
- The exact APK and test APK below passed the isolated API 37 emulator matrix;
  installed hashes were checked by the emulator-only runner.
- Changed working-tree files passed the repository path/content guard; changed
  documentation links and `git diff --check` passed. These checks preceded release staging.

| Android mode | Assertions | Result |
| --- | ---: | --- |
| device-identities | 1100 | PASS |
| recog | 284 | PASS |
| google-cast | 158 | PASS |
| recognition-report | 235 | PASS |
| local-metadata | 60 | PASS |
| scan-details | 74 | PASS |

Total: **1911 Android assertions across six modes**.

## Candidate identity

- Development APK SHA-256: `cf44191b93fb14ef94200073c4f77e1d45c05a048097c4cd6ee9b5c663b46d56`.
- Instrumentation APK SHA-256: `c4d83aa4653072dc81792473816bd505c3ae08d635d6b31e042abedc2ab29fe0`.

The source and test cases contain public product identifiers and synthetic network
identifiers. No household report, address, device name or capture was added.
No permissions, runtime dependency, background work, cloud recognition or CI was added.

## Limits

Synthetic checks establish the covered interpretation and reporting behavior,
not a measured improvement in household recognition accuracy. Physical devices,
real firmware responses, multicast delivery, API 26/36 and low-memory performance
were not retested. The Apple TCP response-read failure is not repaired by these
catalogue aliases; only an independently reported supported model can help identify
that endpoint. Missing/ambiguous models remain unknown. Audio-zone groups are not
counted as independently verified physical hardware, and report capacity loss is
still possible and explicitly counted. No new discovery probe was added.
