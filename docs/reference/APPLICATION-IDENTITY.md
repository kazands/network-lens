# Application identity and updates

Network Lens uses `io.github.kazands.networklens` as its Android application ID
and Kotlin namespace. The visible application name is Network Lens. The test
companion uses `io.github.kazands.networklens.test` and is for isolated emulator
verification only.

## Public APK updates

Public previews retain this application ID and the pinned app-signing certificate.
Download the current signed APK from [GitHub Releases](https://github.com/kazands/network-lens/releases/tag/v0.3.30)
and install it as an update. Public releases support in-place updates with the same signing identity. See the
exact completed checks and their limits in
[release verification](../VERIFICATION-0.3.30.md).

The public and development signing identities are separate. A locally built test
APK cannot update an installation signed with the public key. Preserve the
intended key; do not replace credentials or pinned fingerprints to bypass a
signature mismatch. See [release signing](RELEASE-SIGNING.md).

Versions 0.3.17 and later include an [in-app updater](APP-UPDATES.md). It checks
the same public signing identity and asks Android to install a newer version;
older previews need this release installed once to receive the feature.

## Session state

Results, filters, appearance and scan-range choices stay in memory. A fresh scan
has no inherited recognition or presence evidence. There is no saved history,
Wi-Fi fingerprint or metadata inventory to migrate between installations.
The application cannot read another package's private storage.

## Build identity

Manifest, Gradle metadata, Kotlin packages, permission/security audits and signing
admission use the same application identity. The build-tool override is
`NETWORK_LENS_BUILD_TOOLS`. Direct builds and tests use the supported commands in
[BUILDING.md](../BUILDING.md). Private signing material is never part of Git or a
release download.
