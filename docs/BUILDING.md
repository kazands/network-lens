# Build and test

The supported build uses the direct scripts on Linux. Gradle project metadata is provided for IDE use; it is not the verified release path.

## Requirements

Install a full JDK 21, Bash, Python 3, curl, unzip and zip. Set `JAVA_HOME` to the JDK directory. The repository pins Android SDK platform 37.0 revision 2, build-tools 36.0.0, Kotlin 2.3.21 and D8/R8; setup scripts verify downloaded tool archives.

```sh
export JAVA_HOME=/path/to/jdk-21
bash scripts/bootstrap-sdk.sh
bash scripts/test.sh
bash scripts/build.sh
```

The app supports Android 8.0+ and targets API 37. The development APK is written to `artifacts/Network-Lens-0.3.26.apk`, with a checksum and permission/security audit outputs. Downloaded tools and build artifacts are ignored by Git.

A fresh checkout creates a local development signing key. That key cannot update an installation signed by another key. Existing keys must not be replaced to work around an installation error. Public release credentials are never required for ordinary builds or tests.

## Host tests

`bash scripts/test.sh` checks generated catalogues and coverage policy, compiles the shared Kotlin code, and runs 48 host suites and seven Python diagnostic tests. Some fixtures open bounded loopback sockets; the runner needs permission to use local sockets. It does not require a phone or scan a household network.

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
| Focused products | `google-cast`, `fire-tv`, `smartthings`, `envisalink` |
| Lifecycle and recovery | `service-retries`, `scan-ownership`, `fresh-presence`, `session-only`, `session-cold` |
| Interface | `home-user`, `scan-details`, `typography`, `navigation`, `options` |
| Updates and performance | `app-updates`, `performance` |

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
