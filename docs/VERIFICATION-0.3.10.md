# Network Lens 0.3.10 verification

Verified locally on 2026-10-30 for the 0.3.10 development pre-release. Publication adds the tested APK, corresponding source package, checksums and these notes without changing the tested APK bytes or application code. Older release assets and tags are preserved.

## Application identity

- Application ID and production Kotlin namespace: `io.github.kazands.networklens`.
- Instrumentation ID and namespace: `io.github.kazands.networklens.test`.
- Version: 0.3.10 / code 38; minimum API 26, target API 37.
- Visible label: Network Lens; approved glass-and-metal artwork retained.

All 40 production Kotlin files were compared with the preceding source. They retain their original code apart from the package declaration and the renamed `network_lens` defensive preference-cleanup filename in Store. Build scripts, host imports, reflection targets, emulator selectors, signing admission, packaged permission/resource/class audits and current documentation use the new identity. Current operational source has no references to the old application name or namespace. The 0.3.8 and 0.3.9 verification records preserve their historical identifiers.

The new ID is a separate Android application. It cannot update the earlier previews in place or read their private storage. See [IDENTITY-MIGRATION.md](IDENTITY-MIGRATION.md).

## Artifacts and signing

- Development APK: `artifacts/Network-Lens-0.3.10.apk`.
- Development SHA-256: `8808ccf044c4f435773346ab713a5f18c76bbb59a78ae4e6e8d53b32c073cf5e`.
- App-signing APK: `artifacts/release/Network-Lens-0.3.10.apk`.
- App-signing SHA-256: `97220ff65d71b624cbd827ad4612d1daeff66c54cdbd79ce0b0c967183cf8f92`.
- All 30 application ZIP entries, excluding signature metadata, match between these builds.

Both direct builds passed APK signature verification, permission allowlisting and packaged security checks. The DEX inventory contains the new namespace and no old package/name strings. The release APK retains the pinned app-signing certificate. App/upload public fingerprints are unchanged. Original private app/upload/development keys, passwords and recovery receipt were compared byte-for-byte with their pre-change hashes and remain unchanged. The canonical development-key copy retains the pinned original development certificate with its new alias; no replacement key was generated. Existing certificate subject metadata remains part of that certificate.

## Checks performed

- All 19 host suites passed: 13,735 assertions plus their malformed-input loops.
- All 116 signing integration assertions passed using isolated synthetic credentials. This includes accepting the new package, rejecting a different synthetic package, preserving output on rejected input, key separation, backup admission and application payload equality.
- Both build paths, the Android test APK build, Python syntax and shell syntax checks passed.

| Android API | Instrumentation modes | Checks | Result |
| --- | --- | --- | --- |
| 26 | 6 | 219 | PASS |
| 36 | 22 | 742 | PASS |
| 37 | 8 | 149 | PASS |

API 36 ran all 22 default integration modes, including scanner/UI, native services, reflection paths, fresh presence, RAM-only state, cold restart, branding, navigation, dialogs and optional Wi-Fi-name permission. API 26 ran the six focused branding/navigation/session/options/ICMP modes; the expected platform ICMP denial remained correctly reported as Partial. API 37 ran eight focused permission, denied-ICMP, branding/navigation/session and native engine/resolver modes. The API 37 permission runner targeted the renamed application ID.

The virtual Wi-Fi disconnect/reconnect journey passed all 18 checks on API 36, including cancellation, stale-result clearing, reconnect recovery and no automatic scan.

- API 36: 17 production installation/update/App info checks passed.
- API 37: 18 production installation/update/App info checks passed.

Production checks covered fresh installation, exact installed APK bytes, same-key update, the renamed launcher component, the visible Network Lens label, no automatic scan, and Android Settings opening the correct App info page. API 37 additionally preserved its granted local-network permission across the update. The existing older preview on the API 36 emulator remained installed with unchanged APK bytes.

The AOSP Settings builds used here display version 0.3.10 at the bottom of App info rather than displaying the package ID as a separate field. Package Manager, the packaged manifest, exact installed APK bytes and the renamed foreground activity independently confirm the installed ID. These checks do not claim that every vendor's App info layout is identical.

## Test environment and limits

The initial restricted host run could not open a loopback socket; the same host suites passed with local socket access. A local production-check helper initially assumed that Android echoed the full package URI in command output and that an absent older package lookup returned success. Those helper assumptions were corrected to inspect the actual Settings page and handle the absent-package result. The final production checks passed; no application code was changed to obtain those passes.

All device actions selected explicit emulator serials and verified the emulator property. No physical phone was operated, no household scan data was collected, and no credentials or personal screenshots are included. Local logs, test helpers and emulator screenshots remain ignored under `.tools/` and `artifacts/`. A newly started Android 8 emulator was closed after its checks; the Android 17 test emulator is also closed at the end of verification.

Repository content checks, artwork provenance hashes and `git diff --check` passed before commit. This update does not resolve or claim to resolve the known DNS-SD discovery limitation in [STATUS.md](STATUS.md). Physical-device installation, real-Wi-Fi discovery and Play enrollment remain unverified for this new package identity.

## Public release package

The release contains `Network-Lens-0.3.10.apk`, `Network-Lens-0.3.10-source.zip`, `SHA256SUMS` and `VERIFICATION-0.3.10.md`. The source archive records its exact Git commit and per-file hashes in `RELEASE-SOURCE.json`. It includes the committed project, build/test instructions, original/upstream licenses and notices, checksum-verified Kotlin standard-library sources and the public IEEE fields needed to regenerate the vendor asset. Regeneration was verified byte-for-byte before packaging. The archive excludes private credentials, tool caches, household captures and personal screenshots.

The README button links directly to the versioned 0.3.10 APK asset. Anonymous public downloads are checked against the release package's portable checksums after publication. This remains a development pre-release, with the physical-device and discovery limits above.
