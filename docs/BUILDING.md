# Build and test

The supported build uses the direct scripts on Linux. Gradle project metadata is provided for IDE use; it is not the verified release path.

## Requirements

Install a full JDK 21, Bash, Python 3, curl, unzip and zip. Set `JAVA_HOME` to the JDK directory. The repository pins Android SDK platform 37.0 revision 2, build-tools 36.0.0, Kotlin 2.3.21 and D8/R8; setup scripts verify downloaded tool archives. The UI uses the Kotlin Compose compiler plugin from that same pinned distribution. App and instrumentation bytecode target JVM 11; D8 preserves the Android 8.0 minimum. Host suites continue to target JVM 8.

```sh
export JAVA_HOME=/path/to/jdk-21
bash scripts/bootstrap-sdk.sh
bash scripts/test.sh
bash scripts/build.sh
```

The app supports Android 8.0+ and targets API 37. The development APK is written to `artifacts/Network-Lens-0.3.34.apk`, with a checksum and permission/security audit outputs. Downloaded tools and build artifacts are ignored by Git.

A fresh checkout creates a local development signing key. That key cannot update an installation signed by another key. Existing keys must not be replaced to work around an installation error. Public release credentials are never required for ordinary builds or tests.

## Compose dependencies

The direct build resolves official AndroidX artifacts using a checksum-pinned Gradle 8.13 distribution as a dependency resolver only. `scripts/compose-dependencies/` retains the dependency lock and SHA-256 verification metadata. `scripts/setup-compose-runtime.py` verifies 64 runtime artifacts against `android/compose-runtime-provenance.json`, extracts resources and packages original license notices. The Compose BOM is 2026.11.01; Activity Compose is 1.13.0.

A clean checkout needs network access to Google Maven, Maven Central and the Gradle distribution. Later builds reuse verified files under `.tools/compose-build/`. The direct APK build merges no library manifest components: optional startup, emoji, process-lifecycle and profile-installer initializers are unused. The IDE project has a separate manifest overlay removing those components; Gradle APK output remains unverified.

Dependency updates are deliberate: update coordinates and locks, generate verification metadata with Gradle, run `python3 scripts/setup-compose-runtime.py --refresh`, and review changed hashes, notices, classes and native libraries before rebuilding. The APK audit permits only that exact inventory and the app’s three manifest components. It also verifies native-library hashes and 16 KB ZIP alignment.

The unshrunk Compose candidate is about 15 MB, compared with about 4 MB before migration. This build includes all four native ABIs. Physical startup, memory use and scrolling performance still need measurement.

## Host tests

`bash scripts/test.sh` checks generated catalogues and coverage policy, compiles the shared Kotlin code, and runs 54 host suites and Python diagnostic tests. Some fixtures open bounded loopback sockets; the runner needs permission to use local sockets. It does not require a phone or scan a household network.

Coverage includes protocol parsing, current-response rules, identity conflicts, grouping, retries, scanner/host separation, IPv6 admission, updates and resource ownership. Test counts measure assertions, not supported products or real-world accuracy.

## Android tests

Use an isolated emulator with official Android platform-tools and system images. The runner expects `adb` at `.tools/sdk/platform-tools/adb`, rejects physical devices and checks the installed APK hashes.

```sh
bash scripts/build-android-tests.sh
python3 scripts/test-android-e2e.py --serial emulator-5554 \
  --modes device-identities,service-retries,local-metadata,scan-ownership,home-user,scan-details \
  --output artifacts/local-emulator-check
```

Replace the serial with the exact emulator you started. Instrumentation changes emulator app state and permissions. The `permission` and `icmp-denied` modes require API 37 or newer.

| Area | Useful modes |
| --- | --- |
| Recognition and protocols | `device-identities`, `recog`, `local-metadata`, `homekit`, `routed-ipv6` |
| Focused products | `google-cast`, `fire-tv`, `smartthings`, `envisalink`, `kidde`, `fibe-tv` |
| Indirect local-name evidence | `network-names` |
| Lifecycle and recovery | `service-retries`, `scan-ownership`, `fresh-presence`, `session-only`, `session-cold` |
| Compose migration | `compose-ui`, `compose-typography`, `compose-updates`, `compose-update-network`, `compose-reports`, `compose-rtl`, `compose-privacy`, `compose-list-anchor`, `compose-icmp-errors` |
| Interface | `ui-refinement`, `home-user`, `detail-stability`, `recognition-report`, `scan-details`, `typography`, `navigation`, `options` |
| Updates and performance | `app-updates`, `performance` |

Compose UI checks drive the actual semantics tree, actions and measured text layouts. They use synthetic observations, test both themes, 200% text, narrow and wide viewports, and preserve private-keyboard and sensitive-clipboard protections. They do not substitute fake View widgets.

`compose-privacy` checks every Compose window's capture/autofill exclusions,
Activity recreation and screenshot availability. On API 29+, it supplies an
in-memory recipient to the actual Compose capture manager and checks that no
structure or text is sent. A positive control proves that the same manager would
request structures if capture were enabled. It uses the pinned runtime's internal
test boundary; it does not install or exercise an OEM content-capture service.

`compose-update-network` drives the real network-reconciliation path with
synthetic Wi-Fi loss, replacement and IPv6 withdrawal. It checks that updater
progress, Cancel and the verified-update action stay visible, while obsolete
scan dialogs and reports retire. Its update state and cache bytes are synthetic;
it does not contact GitHub, change the emulator's network or install an update.

`compose-list-anchor` measures actual row positions after responses are added or
removed while details are open, including Activity recreation and a missing
anchor. `compose-icmp-errors` tests a synthetic failed-ICMP report through the
actual Compose sheet in both themes. The native `icmp` mode uses those same
Compose assertions after its injected or platform-denied echo checks.
The `scan-ownership` mode also pauses a real enrichment worker across scan
cancellation, then verifies that rejected resource registration closes its
socket without an uncaught worker exception or continued work.

Legacy UI mode names such as `home-user`, `detail-stability`, `navigation`,
`options`, `scan-details`, `wifi-name`, `branding` and `grouping` now delegate to
the shared Compose functional scenario. Their counts are not independent checks
of every older scenario. Prefer the explicit `compose-*` modes above. The
`permission` mode separately exercises Android's local-network denial/grant
dialog on API 37; the optional Wi-Fi-name permission journey still needs manual
verification for this migration.

The `envisalink` mode now verifies removal of the dedicated TPI transport and manual action; it does not exercise an active alarm-service check.

Use `scripts/test-android-e2e.py` and `tests/android/SmokeInstrumentation.kt` for the complete mode list. Choose modes relevant to the change; record exact APK hashes, API versions, results and limitations.

## Repository checks

```sh
python3 scripts/check-recognition-coverage.py
python3 scripts/check-repository.py
git diff --check
```

Review the staged diff yourself. The repository checker is a guardrail for staged content, not a guarantee that private information is absent. Never force-add ignored files.

Regenerate catalogues only as an intentional data update. Keep upstream notices, provenance and source hashes. The installed app uses bundled recognition data; regeneration is a development task.

## Physical testing and release builds

Physical testing requires an explicitly authorised device and foreground Network Lens access. Emulator automation must never target a phone. Use the [recognition and performance baseline](reference/RECOGNITION-BASELINE.md) and [E2E requirements](../tests/E2E-REQUIREMENTS.md), with private evidence kept out of Git.

Public builds use `bash scripts/build.sh --release` and write under `artifacts/release/`. Follow [Release procedure](RELEASING.md) and [Signing reference](reference/RELEASE-SIGNING.md). The test-signing harness is `bash scripts/test-release-signing.sh`; it uses isolated fixture credentials, not the production keys.
