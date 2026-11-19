# Recognition expansion verification

Unreleased development candidate, still labelled 0.3.25. Not a published release.
These results include scanner/host separation for HTTP capabilities and direct or routed Bonjour advertisements, plus the recovery-completion review fix.

APK SHA-256: `a3036956449956220550a54fd897fa26e4bfaf0baf215e685de36842b736898b`

Instrumentation SHA-256: `2ff7cf623f593840516a4111b27c377d71897784907a51808509cff5673caea8`

## Passed after the review fixes

- Direct application and Android instrumentation builds, including permission/security build audits.
- All 48 host suites and seven Python diagnostic tests passed through the normal host runner. Synthetic loopback fixtures ran with the required local-socket permission.
- Focused host coverage includes 133 equipment checks, 296 cross-family/recovery/scanner checks and 652 mDNS retry checks.
- Scanner regressions preserve a recognised computer's product, reported model, manufacturer and hardware type; separate scanner details survive snapshots and cannot establish host identity through grouping. Unknown hosts stay unknown. Tests now enter actual current-service admission for both `_uscan` and `_uscans`, cover routed advertisements, repeated advertisements before/after HTTP, names and identifiers, transport selection and cancellation. The original review reproduction also passes against the rebuilt application classes.
- Recovery regressions cover a settled discovery coordinator with newly queued work, all 32 recovery types, the final 1.2-second reply window, independent IPv4/IPv6 progress, absent receivers and a negative monotonic-clock origin.
- Checked coverage inventory: all 174 pinned integration domains have explicit interpretation policies.
- Repository hygiene check and `git diff --check` passed.
- The isolated API 37 runner verified the installed application and instrumentation hashes before executing the matrix.

| Android mode | Checks | Result |
|---|---:|---|
| device-identities | 1086 | PASS |
| service-retries | 211 | PASS |
| local-metadata | 60 | PASS |
| scan-ownership | 15 | PASS |
| home-user | 61 | PASS |
| scan-details | 74 | PASS |

Total: **1507 Android assertions across 6 modes**.

The task-owned emulator was stopped afterwards. No phone was operated, and no
release was published or installed on a phone.

## Earlier candidate

Before the review fixes, the original expansion candidate passed 2,659 Android
assertions across ten API 37 modes, plus all 48 host suites against its application
class JAR. Its APK SHA-256 was
`beeaa551a17cfccc9ef2424df3e28578c54a41a335f0c00bd237e3c7aabe2838`.
Those results are historical, not a claim that the full ten-mode matrix was
rerun on the corrected APK. The corrected candidate ran the six relevant modes
listed above; the separate `recog`, `routed-ipv6`, `typography` and `navigation`
modes were not repeated.

## Not verified

- Physical Ubiquiti/MikroTik firmware, actual broadcast/multicast delivery and occupied-port behavior on a phone.
- Real scanner firmware/TLS certificates, wireless power-saving recovery, crowded home networks or cross-VLAN behavior.
- Recognition recall/false-positive rates on a representative physical fleet. Synthetic checks do not establish a household accuracy percentage.
- Physical TalkBack and low-memory phone performance. Native assertions are not a manual physical accessibility review.
- API 26/36 were not verified for these fixes. No store upload, release signing, publication or phone installation was performed.

See [implementation and boundaries](../reference/RECOGNITION-EXPANSION.md) and the existing [physical baseline procedure](../reference/RECOGNITION-BASELINE.md).
