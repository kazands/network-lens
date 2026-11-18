# Network Lens 0.3.24 verification

Prepared on 2026-11-18. Android development preview for home-network testing.

## Identity and changes

- Application ID `io.github.kazands.networklens`; version **0.3.24 / code 52**.
- Android 8.0+ (API 26); target API 37. Public signing identity is unchanged.
- A confirmed current IPv4 endpoint can be recognized as **Envisalink · Alarm
  interface** from its exact DSC TPI greeting. A lock icon accompanies the label.
- The bounded check makes one receive-only connection, independently of whether
  a web port answered. It sends no login, password, alarm command or status request.
- Exact EVL generation, reported manufacturer and MAC remain unknown unless
  independently reported. Generic login prompts do not identify a device.
- No new permissions, cloud recognition, saved scan data or background scans.
- See [Envisalink coverage](ENVISALINK.md) for protocol, provenance and limits.

## Review and candidate checks

Deep kaz-code-review and a fresh independent reviewer approved all 14 changed/new
files without substantive findings. All **45 host suites** and two offline Python
diagnostic modules passed. The focused Envisalink suite passed **38 checks** again
during review; the independent reviewer also passed **152 catalogue checks**.
Its full host rerun stopped at an environment-denied loopback fixture after
compilation and the first 12 suites passed; it subsequently passed the focused
Envisalink suite with loopback access.

The candidate passed **1,304 Android checks on API 37**: `envisalink` (28),
`device-identities` (977), `recog` (284) and `scan-ownership` (15). Installed APK
hashes were verified. The first emulator boot failed its System UI startup guard
before tests ran; the complete matrix passed after a clean restart.

## Versioned artifacts and checks

- Direct development, public-signed and instrumentation builds passed, including
  packaged permission/security audits.
- All **170 reviewed Kotlin application/test sources** are unchanged.
  All **48 payload entries except the manifest** match the reviewed candidate.
- Development APK SHA-256: `0057e0a79913e0bb52690952ac3c999bb10fb974194324e6d7d3a1feeed0da10`.
- Public APK SHA-256: `64f04bdef147b714468df7842c42bc2aaa116001bfdc7b987336517edf0a94e2`.
- Instrumentation APK SHA-256: `00f94b9170e8482c3ebc215f0fc9bf3bc4f07deee9b171aba2f2b9296a71081d`.
- All **49 non-signature payload entries** match between development and public APKs.
- Release-signing tests passed **116 assertions** using isolated test credentials.
- The exact versioned development APK passed **545 Android checks on API 37**:
  `envisalink` (28), `app-updates` (502), `scan-ownership` (15). Installed
  application/test hashes were verified. A first emulator boot failed its system
  startup guard before tests ran; the full matrix passed after a clean restart.
- The exact public APK passed **23 in-place update/launch checks** from public
  0.3.23 on API 37. The pinned certificate, installed bytes, version, application
  UID and runtime permissions were verified. The app opened normally and waited
  for a user-started scan.

## Distribution and remaining checks

The release includes the signed APK, matching tagged source package, verification
notes and SHA256SUMS. The source manifest identifies the exact Git commit/tree,
per-file hashes and separately identified public regeneration inputs. Private
signing material, household data and local caches are excluded.

Physical Android Wi-Fi recognition and coexistence with an active alarm
integration remain unverified. Envisalink allows one TPI client; an occupied slot
may prevent recognition, and a brief probe can coincide with another client's
reconnection. The check never tries to displace an existing client. Honeywell/UNO
login prompts and exact model generation remain outside this recognition method.
No phone was operated for this release. Live in-app download/approval, physical
TalkBack and device-specific compatibility remain separate checks. No GitHub CI
was added; verification is local. This remains a development preview.
