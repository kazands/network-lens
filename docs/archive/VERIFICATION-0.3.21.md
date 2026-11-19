# Network Lens 0.3.21 verification

Prepared on 2026-11-18. Android development preview for home-network compatibility testing.

## Identity and scope

- Application ID `io.github.kazands.networklens`; version **0.3.21 / code 49**.
- Android 8.0+ (API 26); target API 37. The pinned public signing identity is unchanged.
- Fifteen exact Google product profiles cover Home/Nest speakers, Nest Hub smart
  displays, Chromecast/Google TV streaming devices, Chromecast Audio, a Nest Wifi
  point and Pixel Tablet. A supported reported model can identify the product
  without a reported manufacturer. Useful room names remain below the product title.
- Product interpretation does not populate missing reported manufacturer or MAC
  fields. Conflicting evidence suppresses recognition. Virtual Cast groups and
  friendly names alone do not supply hardware identity.
- Shared model strings retain a family title. A `Chromecast` response does not
  establish a Google TV variant, generation or HD/4K edition. An unknown receiver
  still needs usable current evidence; this change does not add a discovery protocol.
- No new permission, background scan, saved inventory, analytics, cloud recognition,
  device pairing or control is added. Scan data and preferences remain in memory.

## Review and implementation checks

A fresh independent Deep kaz-code-review covered all ten implementation, test
and documentation changes and approved them with no substantive findings. The
coordinator verified the surrounding admission, conflict, grouping and presentation
paths and reran all **43 Kotlin host suites** and **seven offline Python tests**.
The new Google host suite passed **285 checks**. The independent reviewer also
ran that suite with the production product/hardware tables loaded, alongside
catalogue and physical-category regressions.

Release preparation changed version metadata, documentation and download links.
All reviewed Kotlin application and test sources remain unchanged. All **47
application payload entries other than the Android manifest** match the reviewed
pre-versioning APK. The earlier candidate emulator checks are retained in
[development status](../STATUS.md#google-device-recognition-in-0321); their old hashes
are not the versioned artifact identities below.

## Exact versioned artifacts

- Direct development, public-signed and instrumentation builds passed, including
  packaged permission/security audits.
- Development APK SHA-256: `ea6123d3e72771ca48d6312c52b102cbdf964c2557c4831c1ca35044e9032b27`.
- Public APK SHA-256: `2134dda511b8d58779a5c33b1e62d3c8b4f6ee6e1ad0418597b3490da887492c`.
- Instrumentation APK SHA-256: `2cdbce9de53994e15da2df78242dc11960f65c32c7ab2a2fb95231ec510706a0`.
- All **48 non-signature payload entries** match between development and public APKs.
- **1,023 API 37 checks passed**: device-identities (855), app-updates (146)
  and scan-efficiency (22).
- **158 API 26 Google checks passed**. Both platform runs verified the exact
  installed application and instrumentation hashes. These are the versioned
  0.3.21 artifacts above, not the earlier candidate.
- All **116 isolated signing assertions** passed using generated fixture keys.
- **23 signed in-place update/launch checks passed** from the original public
  0.3.20 APK to the exact public 0.3.21 APK on an isolated API 37 emulator.
  Installed bytes and the pinned signing certificate matched; the application
  UID and all three established runtime permission grants were retained.
  The updated app launched and waited for a user-started scan.

## Source and distribution

The release includes the signed APK, matching source package, this verification
record and `SHA256SUMS`. The source package includes tracked project files and
the separately identified public regeneration inputs retained from the preceding
verified source release. Its `RELEASE-SOURCE.json` identifies the exact Git commit,
tree, per-file hashes, public source additions and catalogue provenance.
No signing credentials, household captures, personal screenshots or local tool
caches are distributed. Current README/download links point to this release.

## Remaining verification boundaries

The new APK has not been installed or recognition-tested on a physical phone.
The emulator fixtures establish admission and presentation behavior, not real
Wi-Fi coverage, every Google receiver/firmware combination, physical TalkBack
operation or household recognition accuracy. Devices that omit a usable model
or do not answer remain unresolved. Existing HomeKit/HTTP address-resolution and
Spotify endpoint failures remain explicit in Scan details.

The signed emulator upgrade checks installation separately from the app's
interactive download/Android-approval journey. Physical in-app installation and
Google Play enrollment remain unverified. This remains a development preview.
