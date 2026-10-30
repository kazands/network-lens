# Network Lens 0.3.9 verification

This is an unreleased branding update, verified locally on 2026-10-30. The published download remains the 0.3.8 development preview.

## Current artwork

The user approved the hyper-realistic dark teal metal Wi-Fi/lens on a frosted mint glass disc, replacing the earlier Balanced flat design. The approved studio image and transparent-background adaptation are retained under `android/branding/`. The built-in image-generation tool removed the studio backdrop; a 512px export of that adaptation is shared by the launcher and 56dp header without tinting. The launcher has a proportional central 72/108 viewport and a full mint background. Android themed icons retain a simplified single-colour two-arc Wi-Fi/lens companion.

The store icon is a clean 512px full-field export of the actual Android drawable, with no baked launcher mask. Source details, generation prompts and hashes are in `android/branding/provenance.json`. The earlier SVG is retained as a flat reference for the monochrome companion.

The application ID remains `dev.netlens`, version 0.3.9 / code 37. The production change is limited to artwork and the header image binding.

## Current artifacts

- Development APK: `artifacts/Network-Lens-0.3.9.apk`.
- Development SHA-256: `b8b1ccd7169ff5f74bf8d882e34bc057f399a90246e650022600d992b7d2195a`.
- App-signing APK: `artifacts/release/Network-Lens-0.3.9.apk`.
- App-signing SHA-256: `946d16a4168f371bb5dda8656bb90f8689ccff649b66d3e6c2f2f4a4d42bbc48`.
- All 30 application ZIP entries, excluding signature metadata, match the development APK installed and hash-verified by the emulator runner.
- The existing pinned app-signing identity and recovery verification gate passed; no signing identity was replaced.

## Checks performed on the current artwork

- Direct development build and `--release` build passed using the bundled JDK 21, pinned Kotlin compiler and Android SDK.
- Both builds passed APK-signature verification and packaged permission/security checks. These checks are not a vulnerability certification.
- Android 16 / API 36 isolated emulator: 23 branding checks passed, covering opaque launcher output, mint background, teal/mint material shading, centring, circle/squircle/rounded-square masks, dedicated monochrome artwork, the shared untinted header in light/dark mode, and large-text capture.
- The same application APK passed all 31 native navigation checks, including returning from details and preserving list position.
- Native captures of light mode, dark mode, large text and launcher masks were inspected. The glass/metal finish remains visible and the full emblem fits its frame.
- The store icon exactly matches the final native full-field export. Artwork provenance hashes, changed-file repository checks, staged repository checks and `git diff --check` passed.

The first branding run failed because its new material-colour check only counted pixels with alpha exactly 255. The frosted-glass source predominantly uses alpha 253. The corrected check counts visible material pixels with alpha greater than 200, still requiring substantial teal and mint areas and more than 256 distinct colours. All final branding checks passed. The navigation check passed on the first run against the same application APK.

Ignored local evidence is under `artifacts/hyperreal-logo-api36/`, `artifacts/hyperreal-logo-final-api36/`, `artifacts/hyperreal-android-tests-build.txt` and `artifacts/hyperreal-release-build.txt`. All screenshots use synthetic emulator sample devices.

## Limits and earlier checks

The current hyper-realistic artwork has not been checked on API 26, API 37 or the physical Pixel. Host discovery suites and the synthetic signing integration harness were not rerun for this artwork-only change. No physical device was operated. No GitHub release or Play listing was published.

The earlier Balanced flat artwork had separate successful builds, 19 host suites (13,735 checks plus malformed-input loops), 116 signing integration assertions, 21 branding checks on API 26 and 23 branding plus 31 navigation checks on API 36. Its API 37 emulator failed to finish cold boot within the three-minute startup deadline, before branding could run. Those results and the previous production APK hash refer to the superseded flat design, not the current artwork.

This update does not constitute a new real-Wi-Fi scan test or resolve the discovery limitations recorded in [STATUS.md](STATUS.md).
