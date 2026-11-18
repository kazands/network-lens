# Network Lens 0.3.22 verification

Prepared on 2026-11-18. Android development preview for home-network compatibility testing.

## Identity and scope

- Application ID `io.github.kazands.networklens`; version **0.3.22 / code 50**.
- Android 8.0+ (API 26); target API 37. The pinned public signing identity is unchanged.
- Explicit Amazon WhisperPlay queries read useful friendly names and a separate
  public device-type code. One reviewed code identifies Amazon Fire TV Stick 4K;
  generation remains unspecified. Other qualifying advertisements retain the
  Amazon Fire TV service-family label. Internal identifiers are not friendly titles.
- SSDP adds bounded DIAL searches and accepts the documented same-peer Fire TV
  description path. Existing scope, deadlines, response validation and cancellation
  still apply. No pairing, login or control is performed.
- Known Amazon hardware evidence constrains conflicting Apple/Google model
  interpretations and physical categories, including grouped addresses. Missing
  reported Manufacturer, Model and MAC values stay unknown.
- No new permission, saved inventory, analytics, cloud recognition or background
  scan is added. Scan data and preferences remain in memory.

## Review and implementation checks

A fresh independent Deep kaz-code-review approved the complete implementation
with no substantive findings. All **44 Kotlin host suites**, **seven generated-data
checks** and **seven offline Python tests** passed before release versioning.
The Amazon host suite passed **86 checks**. The independent reviewer ran 13 focused
host suites (**2,937 checks**); the coordinator also passed **78** ordering, copying,
grouping and valid-identity checks. Synthetic fixtures contain no household data.

Release preparation changed version metadata, documentation and download links.
All reviewed Kotlin application and test sources remain unchanged. All **48
application payload entries other than the Android manifest** match the reviewed
pre-versioning APK. Earlier candidate checks and research are described in
[Fire TV recognition](FIRE-TV-RECOGNITION.md); their artifacts are distinct from
the exact versioned builds below.

## Exact versioned artifacts

- Direct development, public-signed and instrumentation builds passed, including
  packaged permission/security audits.
- Development APK SHA-256: `dd6bc33dc994467acfa6600d17e2692e23997ee37e8a88f367a808da1b9b2fa0`.
- Public APK SHA-256: `6dbaf0ef1a48383c734956b09d58b4fe828d760e4e57c846be40a7e547843614`.
- Instrumentation APK SHA-256: `65e82381e81d206e4742dfcb41eae8a657718a3eaf45e2c019a3c33b503d7bd0`.
- All **49 non-signature payload entries** match between development and public APKs.
- All **116 isolated signing assertions** passed using generated fixture keys.

- **1,166 API 37 checks passed across the initial run and one isolated retry**:
  fire-tv (72), device-identities (926), app-updates (146) and scan-efficiency (22).
  The initial run failed scan-efficiency because its final-list assertion found
  an empty list. The unchanged APK and test passed the isolated retry. This is
  not a clean uninterrupted matrix pass; the intermittent fixture/lifecycle
  timing failure remains a test limitation, and its exact trigger is unconfirmed.
- Installed application and instrumentation hashes were verified on both runs.

- **72 API 26 Fire TV checks passed** on the same versioned APK, with exact
  installed application and instrumentation hashes verified.
- **23 signed in-place update/launch checks passed** from the original public
  0.3.21 APK to the exact public 0.3.22 APK on an isolated API 37 emulator.
  Installed bytes and the pinned certificate matched; the application UID and
  all three established runtime permission grants were retained. The updated
  app launched and waited for a user-started scan.

## Source and distribution

The release includes the signed APK, matching source package, this verification
record and `SHA256SUMS`. The source package includes tracked project files and
separately identified public regeneration inputs retained from the preceding
verified source release. Its `RELEASE-SOURCE.json` identifies the exact Git commit,
tree, per-file hashes, public source additions and catalogue/protocol provenance.
The reviewed Fire TV fact retains its upstream MIT notice and provenance.
No signing credentials, household captures, personal screenshots or local tool
caches are distributed. Current download and documentation links point to 0.3.22.
No GitHub CI workflows are configured; these are local build and test results.

## Remaining verification boundaries

The new APK has not been installed or recognition-tested on a physical phone.
Emulator fixtures establish admission, conflict handling and presentation behavior;
they do not establish real Wi-Fi coverage, every Fire TV firmware combination,
physical TalkBack behavior or household recognition accuracy. Unknown type codes
and devices that omit usable current identity evidence can remain unresolved.
Existing HomeKit/HTTP resolution and Spotify endpoint failures remain explicit.

Signed emulator upgrade checks installation separately from the app's interactive
download/Android-approval journey. Physical in-app installation and Google Play
enrollment remain unverified. This remains a development preview.
