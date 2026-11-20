# Network Lens 0.3.31 verification

Prepared on 2026-11-19. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **59**.

## Reviewed changes

Corroborated local network names can provide explicitly tentative **Likely Hunter Hydrawise** and supported **Likely Orbit B-hyve** family suggestions for current responders. Router names stay separate from device-reported identity and cannot establish presence, grouping, an exact model or a reported manufacturer. Stronger device evidence takes precedence. Default troubleshooting reports mask the names. Recognised Kidde detectors use the Lucide smoke-alarm icon.

Deep independent review approved the complete implementation after regression fixes for no-resolver admission and emulator startup safety. Release preparation changes version metadata and documentation. All 51 APK payload entries other than the manifest match the reviewed candidate; all 52 non-signature entries match between development and public builds.

## Passed checks

- The reviewed implementation candidate passed all 54 Kotlin host suites and the Python diagnostic checks, including 13 instrumentation-startup assertions, 119 local-name assertions and 10,000 malformed DNS packets. The host suite was not rerun solely for release version and documentation changes.
- Versioned development, public-signed and instrumentation builds passed, including packaged permission/security audits and pinned signing-identity checks.
- The exact versioned development APK passed the API 37 matrix below in one run; the runner verified installed app and test hashes.
- The exact public APK passed 23 in-place upgrade and launch checks from public 0.3.30: certificates, installed bytes, versions, UID, existing runtime grants, launch and no automatic scan. Only an isolated emulator was operated.
- Recognition coverage, repository hygiene, current documentation links and whitespace checks passed. No GitHub CI is configured or was added.

| Android mode | Assertions | Result |
| --- | ---: | --- |
| network-names | 42 | PASS |
| kidde | 39 | PASS |
| device-identities | 1277 | PASS |
| recognition-report | 235 | PASS |
| app-updates | 553 | PASS |

Total: **2146 Android assertions across five modes**. Some modes exercise overlapping behavior; this is not a count of distinct scenarios.

## Artifact identity

- Public APK SHA-256: `cebfcd98788835da273dcb980f92e33e883bed87e13d192eb2471bdfc3fa485c`.
- Development APK SHA-256: `12dffe3a783fa24cd9618622db3f1c0deadfff067acc6434c418f7913aeb9f31`.
- Instrumentation APK SHA-256: `e034cf69eaeaaa3958acf6ede5c37c5a8b9d6c4f83f37b48366076bf404c5559`.
- Reviewed candidate SHA-256: `d5497b0a8e5e6c3127060678a9bff87c6f3e9353dbb30c635707ce884753ea96`.

The public certificate remains pinned to `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
The matching source archive identifies the merged Git commit/tree, per-file hashes and separately listed public regeneration inputs. Credentials, household data and local caches are excluded. Asset checksums are supplied in `SHA256SUMS`.

## Limits and required physical checks

Physical Wi-Fi DNS transport, Hunter/B-hyve firmware compatibility and recognition accuracy across routers remain unverified for this APK. Names can be stale even when reverse and forward lookups agree; these are tentative family suggestions, not verified product identity. The BH1 pattern has narrower evidence than the Hydrawise pattern. Unsupported names and devices may remain unnamed.

The reported Google Play Protect classification remains unresolved. Successful emulator installation does not establish Google clearance or explain the warning. No claim is made that this release removes it; keep Play Protect enabled.

No phone was operated. Real /22 timing, multicast reliability, TalkBack, low-memory performance and API 26/36 were not retested. Live in-app internet download and physical installation remain separate checks. There are no new permissions, Bluetooth access, cloud recognition, account access, background scans, saved history or irrigation controls. Local DNS queries are new and bounded to eligible resolvers on the selected Wi-Fi.
