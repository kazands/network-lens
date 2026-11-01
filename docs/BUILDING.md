# Building and testing

## Supported direct build

The verified direct scripts target Linux with a full JDK 21, Bash, Python 3, curl, unzip and zip. Install these through your normal package manager/vendor. Set `JAVA_HOME` to your JDK; the scripts do not install one.

```sh
export JAVA_HOME=/path/to/jdk-21
bash scripts/bootstrap-sdk.sh
bash scripts/test.sh
bash scripts/build.sh
```

`bootstrap-sdk.sh` downloads the pinned Google Android SDK platform 37.0 revision 2 and Linux build-tools 36.0.0 archives, verifies their SHA-256 digests and extracts them into `.tools/sdk`. The build also accepts standard SDK layouts through `ANDROID_SDK_ROOT` and `NETWORK_LENS_BUILD_TOOLS`.

`setup-kotlin-toolchain.py` obtains and verifies the Kotlin compiler 2.3.21, standard library and R8/D8 against `scripts/kotlin-toolchain.json`. They are cached under `.tools/kotlin-0300`. The build uses D8; it does not currently enable R8 code shrinking. Tool archives and outputs are ignored by Git.

The current source builds `artifacts/Network-Lens-0.3.11.apk`, with a checksum and permission/security audit outputs. It retains `io.github.kazands.networklens` and updates the production-signed 0.3.10 preview in place. Previews before 0.3.10 use a different application ID; see [IDENTITY-MIGRATION.md](IDENTITY-MIGRATION.md). Recheck the version in the manifest/build scripts when preparing a future release. Gradle metadata is provided under `android/`; no Gradle wrapper is included. The direct scripts are the verified build path.

## Signing

On a fresh checkout, `prepare-signing.py` creates `.tools/network-lens-development.jks` and `.tools/network-lens-signing-password`, using the `network-lens` alias and restrictive file permissions. It never prints the password. Do not commit either file. Keep any key you depend on in a secure backup that you control.

A new key cannot update an installation signed by another key. Do not replace or delete an existing signing key to resolve an install failure. Contributor-built APKs are unofficial; maintainer previews are distributed through [GitHub Releases](https://github.com/kazands/network-lens/releases). Source licensing is GPL-3.0-only with third-party exceptions described in NOTICE.

Maintainer release APKs use a separate pinned app-signing identity and verified recovery receipt. Run `bash scripts/build.sh --release`; output goes under `artifacts/release/`, preserving development outputs. Release builds never create keys. A separate upload identity is prepared for future Play enrollment. See [RELEASE-SIGNING.md](RELEASE-SIGNING.md) for private files, backup verification, migration and remaining Play work.

`bash scripts/test-release-signing.sh` runs the Kotlin signing integration harness after a development APK has been built. It generates isolated fixture keys and never reads real production credentials. These build checks are separate from the 22 application host suites below.

## Host suites

```sh
bash scripts/test.sh
```

The script compiles the shared Kotlin logic and Kotlin behavior tests, then runs 22 suites. Some checks use bounded loopback fixtures. It does not scan a household network or require a phone. Both test build scripts reject stale Java test sources; generated Android resource code remains part of the normal application build.

## Android integration tests

Install Android platform-tools and an appropriate emulator/system image through the official Android SDK tools. The test scripts expect `adb` at `.tools/sdk/platform-tools/adb`. Start an isolated emulator before selecting its exact serial.

```sh
bash scripts/build-android-tests.sh
python3 scripts/test-android-e2e.py --serial emulator-5554 \
  --modes local-metadata,scan-efficiency,typography,failure-reporting,scan-details,options,navigation,session-only,session-cold,fresh-presence,routed-ipv6,homekit,resolver-upgrade \
  --output artifacts/local-emulator-check
```

Replace the example serial with your actual emulator serial. The scripts reject physical devices and verify the emulator property and installed APK bytes. Instrumentation intentionally changes emulator app state and permissions. API 26, 36 and 37 have been tested previously; do not claim a platform passed unless you actually tested it.

The instrumentation companion is Kotlin and uses the target application's existing pinned Kotlin standard library. Its compiler receives the application JAR as a friend module for internal test access. No additional application library or permission is introduced. The `permission` and `icmp-denied` modes require API 37 or newer; keep their existing API guards intact when selecting modes.

The `local-metadata` mode uses synthetic devices without an Android Network, so
it cannot query LAN devices. It checks current-advertisement metadata, native
cache quarantine, request admission, source labels, cancellation and fresh-scan
isolation. Host loopback fixtures separately exercise HTTP/IPP framing, deadlines
and TLS-only IPPS behavior. See [LOCAL-METADATA.md](LOCAL-METADATA.md) for supported
fields and limits. Real-device compatibility remains a separate check.

The `typography` mode uses synthetic dual-stack and IPv6-only devices. It checks full address wrapping, scoped IPv6, exact copying and IPv4/IPv6 order at 320dp and 411dp widths, normal and 200% font settings, and both appearances. It never starts discovery.

Physical Wi-Fi checks are separate, explicitly authorized manual journeys. Never automate arbitrary connected devices or publish household results. See `tests/E2E-REQUIREMENTS.md`.

## Repository hygiene

Before committing:

```sh
python3 scripts/check-repository.py
```

This checks staged paths and content for common credentials, private keys and unintended files. It is a guardrail, not a guarantee. Review `git diff --cached` yourself. Synthetic fixtures are appropriate; personal network data is not. Never force-add ignored local tools, captures or signing material.

Refresh the offline IEEE data only as an intentional maintenance change with `python3 scripts/update-ieee-vendors.py --refresh`, then review provenance, terms and tests. The installed app never performs an online vendor lookup.
