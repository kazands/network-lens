# Development status

Network Lens 0.3.11 is a development preview. It uses the application ID `io.github.kazands.networklens` and retains the approved hyper-realistic glass-and-metal artwork. It updates the production-signed 0.3.10 preview in place. Previews before 0.3.10 install separately; see [IDENTITY-MIGRATION.md](IDENTITY-MIGRATION.md). The source was organized into a standalone repository on 2026-10-29. Repository setup, artwork and performance changes do not establish production readiness.

The earlier branding checks are recorded in [VERIFICATION-0.3.9.md](VERIFICATION-0.3.9.md). The 0.3.10 package-identity checks are recorded in [VERIFICATION-0.3.10.md](VERIFICATION-0.3.10.md). Current typography and IPv6 layout verification is recorded in [VERIFICATION-0.3.11.md](VERIFICATION-0.3.11.md).

## Typography and IPv6 layout (0.3.11)

Native text styles now share scalable sizes and leading, with larger supporting text, medium/semibold emphasis and sentence-case detail labels. IPv6 remains below IPv4 and wraps between hexadecimal groups without inserted characters, ellipsis or automatic font shrinking. Full scoped addresses remain selectable and copy unchanged. This is a presentation change; discovery behavior and the known DNS-SD limitation remain unchanged.

## Scan efficiency update

Version 0.3.8 avoids redundant presence probes after a validated response in the same scan and defers device-list snapshots until the existing presentation queue needs them. Independent detail lookups, fresh-scan isolation, confirmation rules, retry budgets and discovery deadlines remain. These changes do not resolve the intermittent DNS-SD limitation below. Controlled benchmarks and physical-device timing comparisons must distinguish CPU/allocation reductions from total scan duration and device coverage.

## Known discovery limitation

Repeated physical-device scans reproduced intermittent resolution deadlines for HomeKit (`_hap._tcp`) and HTTP (`_http._tcp`). Android registrations sometimes supplied no usable address update within both bounded attempts. Other discovery methods continued, and the scan correctly remained Partial.

Controlled diagnostic experiments have not established whether device behavior, packet delivery, Android resolver/cache behavior or another interaction causes the silence. A longer timeout and a replacement resolver library have not been demonstrated to solve it. Version 0.3.7 preserves full bounded errors, publishes coherent terminal diagnostics, names affected methods in Scan details and clarifies the unused IPv6-check wording. It does not suppress these failures.

## Previous release validation

The accepted 0.3.7 development APK passed 19 host suites (13,735 assertions), 30 focused Android suites across API 26/36/37 (1,375 checks), packaging/security audits and 20 final phone journey checks. Both final physical scans remained Partial for the two methods above. Passing reporting tests is not a claim that resolution succeeded.

These are historical results for the accepted build, not a fresh test claim for arbitrary future commits. Personal scan evidence and historical diagnostic tools are retained locally and are excluded from the public repository.

## Before production

Further discovery reliability work, real Thread-network validation and broader device testing remain. Google Play enrollment, listing/privacy declarations and a verified App Bundle pipeline are separate future work. GitHub development previews are intended to gather feedback; they do not establish production readiness.

## Version 0.3.8 preview verification

The production-signed APK passed signature, alignment and package audits, 40 installation/update checks across API 26/37 and a physical Pixel 8 journey. That phone journey recorded 17 migration checks and 86 unique UI/scan conditions, three Complete scans and one intentionally stopped Partial scan. Stronger, endpoint-verified navigation reruns covered both fully visible and partly clipped rows after two inconclusive coordinate-based attempts. No app fix or cause for those earlier results is claimed.

The signing identities were independently backed up and exercised through restored-key signatures. The source package includes upstream source/notices and public IEEE inputs whose regeneration matched the bundled vendor asset byte for byte. Exact artifact identities, historical test attempts and remaining limits are recorded in [VERIFICATION-0.3.8.md](VERIFICATION-0.3.8.md). No household inventory or private signing material belongs in those public notes.

Final physical checks additionally covered Home cancellation, a full process restart, a verified Wi-Fi connection replacement and local-network permission denial/restoration. Recovery scans still reproduced the known HomeKit/HTTP resolution failure; passing lifecycle checks do not repair it.

## Release-signing preparation

The direct APK pipeline has an explicit `--release` path, separate production/app and Play-upload identities, pinned public fingerprints and a recovery-verification gate. Existing development signing and artifacts are preserved. See [RELEASE-SIGNING.md](RELEASE-SIGNING.md). This source configuration alone does not claim a successful backup, signed binary, Android installation test, Play enrollment or public release; current verification belongs to its exact artifact/report. A verified App Bundle build and Play Console setup remain separate work.

## Repository migration verification (2026-10-29)

The prepared public source export passed all 19 host suites / 13,735 assertions and built both the app and Android instrumentation companion. Permission/security package audits passed. Its application ZIP payload matched the accepted 0.3.7 APK; signing was tested with a separate temporary development key. The existing accepted APK, source bundle and signing files were preserved. Production Kotlin and Android resources were unchanged. Household-derived fixture identifiers were replaced with synthetic values. No new Android runtime or physical-phone journey was run for this repository-only change.
