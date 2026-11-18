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

The current source builds `artifacts/Network-Lens-0.3.23.apk`, with a checksum and permission/security audit outputs. It retains `io.github.kazands.networklens` and the pinned public signing identity; see [application identity](APPLICATION-IDENTITY.md). Recheck the version in the manifest/build scripts when preparing a future release. Gradle metadata is provided under `android/`; no Gradle wrapper is included. The direct scripts are the verified build path.

## Signing

On a fresh checkout, `prepare-signing.py` creates `.tools/network-lens-development.jks` and `.tools/network-lens-signing-password`, using the `network-lens` alias and restrictive file permissions. It never prints the password. Do not commit either file. Keep any key you depend on in a secure backup that you control.

A new key cannot update an installation signed by another key. Do not replace or delete an existing signing key to resolve an install failure. Contributor-built APKs are unofficial; maintainer previews are distributed through [GitHub Releases](https://github.com/kazands/network-lens/releases). Source licensing is GPL-3.0-only with third-party exceptions described in NOTICE.

Maintainer release APKs use a separate pinned app-signing identity and verified recovery receipt. Run `bash scripts/build.sh --release`; output goes under `artifacts/release/`, preserving development outputs. Release builds never create keys. A separate upload identity is prepared for future Play enrollment. See [RELEASE-SIGNING.md](RELEASE-SIGNING.md) for private files, backup verification, migration and remaining Play work.

`bash scripts/test-release-signing.sh` runs the Kotlin signing integration harness after a development APK has been built. It generates isolated fixture keys and never reads real production credentials. These build checks are separate from the application host suites below.

## Host suites

```sh
bash scripts/test.sh
```

The script checks the reproducible Recog and global product tables, compiles the shared Kotlin logic and Kotlin behavior tests, then runs 44 suites and two offline Python diagnostic-tool test modules. Some checks use bounded loopback fixtures. It does not scan a household network or require a phone. Both test build scripts reject stale Java test sources; generated Android resource code remains part of the normal application build.

## Android integration tests

Install Android platform-tools and an appropriate emulator/system image through the official Android SDK tools. The test scripts expect `adb` at `.tools/sdk/platform-tools/adb`. Start an isolated emulator before selecting its exact serial.

```sh
bash scripts/build-android-tests.sh
python3 scripts/test-android-e2e.py --serial emulator-5554 \
  --modes audio-zones,device-identities,recog,local-metadata,scan-efficiency,typography,failure-reporting,scan-details,options,navigation,session-only,session-cold,fresh-presence,routed-ipv6,homekit,resolver-upgrade \
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

The `recog` mode uses synthetic model advertisements without an Android Network.
It checks current-wire admission, cached-callback exclusion, conflicts, fresh-scan
isolation, Stop, grouping and icon precedence. Native list/details, search,
clipboard, accessibility labels and wrapping are checked in both appearances at
320dp/411dp and normal/200% text. It captures synthetic screenshots only.
See [RECOG.md](RECOG.md) for the deliberately limited model coverage.

The `device-identities` mode checks the local product catalogue, current console
reply admission and the cleaner list/detail presentation with synthetic fixtures.
See [DEVICE-IDENTITIES.md](DEVICE-IDENTITIES.md) for aliases, sources and wire limits.

Google model checks include a focused `google-cast` mode, also included
in `device-identities`. It exercises current model admission without a reported
manufacturer, physical categories/icons, useful room names, name-only HTTP
responses, grouped endpoints, cached/group exclusions, conflicts and Stop.
Fixtures use synthetic addresses without an Android Network and reject phones.

Amazon discovery has a focused `fire-tv` mode, also
included in `device-identities`. See [Fire TV recognition](FIRE-TV-RECOGNITION.md)
for behavior, evidence, regression coverage and physical-verification limits.

Physical Wi-Fi checks are separate, explicitly authorized manual journeys. Never automate arbitrary connected devices or publish household results. See `tests/E2E-REQUIREMENTS.md`.

## Repository hygiene

Before committing:

```sh
python3 scripts/check-repository.py
```

This checks staged paths and content for common credentials, private keys and unintended files. It is a guardrail, not a guarantee. Review `git diff --cached` yourself. Synthetic fixtures are appropriate; personal network data is not. Never force-add ignored local tools, captures or signing material.

Refresh the offline IEEE data only as an intentional maintenance change with `python3 scripts/update-ieee-vendors.py --refresh`, then review provenance, terms and tests. The installed app never performs an online vendor lookup.

## In-app update checks

The host suite includes `AppUpdatesTest`. Build the application and instrumentation
companion with the direct scripts above, then run `app-updates` with
`test-android-e2e.py` on an explicitly selected isolated emulator. The mode uses
synthetic release states, never downloads from GitHub, and abandons the actual
installer session after observing Android's user-approval callback. It does not
install an update or operate a physical phone. Development signing is deliberately
rejected by public-update admission. See [APP-UPDATES.md](APP-UPDATES.md) for the
remaining public-release end-to-end verification boundary.

## TP-Link recognition checks

The host runner includes `TpLinkDiscoveryTest`. The `device-identities` Android
mode includes synthetic plug/dimmer callbacks and presentation checks. See
[TP-Link discovery](TP-LINK.md) for supported protocols and limits.

## Omada and EAP checks

The host runner includes `OmadaDiscoveryTest`. The `device-identities` Android
mode includes synthetic AP/switch announcements and public EAP identity callbacks,
including scope, cancellation, conflicts and presentation. See [coverage and
protocol provenance](OMADA.md). These methods require separate physical
Wi-Fi verification; no household captures belong in fixtures.

## Broader discovery checks

Normal host/build scripts verify the pinned HA matcher projection and Matter
registry offline. Host suites include `DiscoveryMatchersTest`, `MatterInfoTest`,
`LanIdentityTest`, `LifxIdentityTest` and `EasySmartDiscoveryTest`; Android
`device-identities` also checks registry/family presentation and preservation
of conflicting observations. See [DISCOVERY-COVERAGE.md](DISCOVERY-COVERAGE.md).

The `home-user` Android mode uses synthetic devices to verify friendly unknown-device
copy, source-specific recognition explanations, collapsed raw observations, and
button accessibility roles at normal and enlarged text sizes. It rejects physical
devices.

## Scan ownership and performance checks

The `scan-ownership` and `performance` emulator modes are included in 0.3.23.
Run them with the same emulator-only runner and exact APK hashing shown above.
`scan-ownership` uses synthetic endpoints; `performance` records index loading,
snapshot/grouping and sample Activity launch-to-idle without LAN discovery.
Performance output is explicitly synthetic, not physical-phone speed or accuracy.
See [scan architecture](SCAN-ARCHITECTURE.md) and
[physical baselines and measurement limits](RECOGNITION-BASELINE.md).
