# Development status

Network Lens 0.3.7 is a development preview. The source was organized into a standalone repository on 2026-10-29. This repository setup does not change the previously accepted APK or establish production readiness.

## Known discovery limitation

Repeated physical-device scans reproduced intermittent resolution deadlines for HomeKit (`_hap._tcp`) and HTTP (`_http._tcp`). Android registrations sometimes supplied no usable address update within both bounded attempts. Other discovery methods continued, and the scan correctly remained Partial.

Controlled diagnostic experiments have not established whether device behavior, packet delivery, Android resolver/cache behavior or another interaction causes the silence. A longer timeout and a replacement resolver library have not been demonstrated to solve it. Version 0.3.7 preserves full bounded errors, publishes coherent terminal diagnostics, names affected methods in Scan details and clarifies the unused IPv6-check wording. It does not suppress these failures.

## Previous release validation

The accepted 0.3.7 development APK passed 19 host suites (13,735 assertions), 30 focused Android suites across API 26/36/37 (1,375 checks), packaging/security audits and 20 final phone journey checks. Both final physical scans remained Partial for the two methods above. Passing reporting tests is not a claim that resolution succeeded.

These are historical results for the accepted build, not a fresh test claim for arbitrary future commits. Personal scan evidence and historical diagnostic tools are retained locally and are excluded from the public repository.

## Before production

Further discovery reliability work, real Thread-network validation and broader device testing remain. Release signing/key recovery, Google Play listing/privacy declarations and a complete reproducible release/license package need to be checked before publishing an official binary. There is no published official binary release here yet.

## Repository migration verification (2026-10-29)

The prepared public source export passed all 19 host suites / 13,735 assertions and built both the app and Android instrumentation companion. Permission/security package audits passed. Its application ZIP payload matched the accepted 0.3.7 APK; signing was tested with a separate temporary development key. The existing accepted APK, source bundle and signing files were preserved. Production Kotlin and Android resources were unchanged. Household-derived fixture identifiers were replaced with synthetic values. No new Android runtime or physical-phone journey was run for this repository-only change.
