# Network Lens application identity

Version 0.3.10 uses `io.github.kazands.networklens` as both its Android application ID and Kotlin namespace. Android may display this identifier in App info; it is a reverse-domain identifier, not a clickable GitHub link. The visible name remains Network Lens. The companion instrumentation package is `io.github.kazands.networklens.test`.

## Installing the renamed application

The new ID is a separate Android application. It can coexist with the earlier previews and cannot update them in place. Install the new APK, grant its Android-managed permissions when requested, and start a new scan. You can remove the previous preview yourself after checking the new installation. No script in this update removes the old installation or operates a physical phone.

Results, filters and appearance stay in memory, so there is no saved scan history or preferences to transfer. The renamed app cannot read another package's private storage. Its defensive preference cleanup uses the current `network_lens` filename in both credential and device-protected storage; it does not open the old application's sandbox.

## Signing and build tools

The app-signing and Play-upload certificates, private key files and verified recovery receipt are preserved. Only the public pinned application ID and admission checks change. The maintainer development key is copied to the canonical `network-lens-development.jks` name with the `network-lens` alias while preserving its certificate and the original copy. Existing development certificate subject metadata is retained as part of that certificate; changing it would change the signing identity. No replacement signing key is generated for this migration.

The Android build-tool override is now `NETWORK_LENS_BUILD_TOOLS`. Kotlin source, host tests, Android instrumentation, emulator selectors, packaged class/permission audits and release-signing admission use the new namespace. Current scripts no longer target the old package. Historical verification documents remain accurate records of the original builds, and existing GitHub release assets and tags are unchanged.

Once a release is published under this ID, future updates must preserve the ID and the matching app-signing identity. See [Android application IDs](https://developer.android.com/build/configure-app-module) and [Android update requirements](https://developer.android.com/google/play/app-updates).
