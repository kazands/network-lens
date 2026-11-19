# Network Lens 0.3.16 verification

Release prepared on 2026-11-16. Android development preview for physical testing.

## Identity and scope

- Application ID: `io.github.kazands.networklens`.
- Version 0.3.16 / code 44; minimum API 26, target API 37.
- Public signing identity remains pinned in `android/release-signing.json`.
- Apple current-service ProductType lookup; nested Cast metadata; 651 qualified
  Android hardware-category pairs across 24 brands.
- VSSL/Sonos hardware-zone relationships; HEOS, MusicCast and BluOS read-only
  metadata/group readers; AirPlay/Cast group observations separate from hardware.
- Bounded metadata draining allows discovery responses to finish dependent reads.
  Stop/deadline cancellation rejects late responses and closes resources.

## Completed implementation checks

- All 31 host suites passed, including Apple lookup (70), Android recognition (56),
  VSSL/Sonos (70) and broader audio (64) checks.
- Direct development app build, permission/security audits and instrumentation build passed.
- The reviewed implementation passed all 710 checks on an isolated API 37 emulator:
  audio-zones 62, device-identities 544, grouping 33, local-metadata 49,
  scan-efficiency 22. Installed application/test APK hashes were verified.
- Details and copying were tested in both themes, normal/200% text; player ports,
  current-only relationships, replacement/cancellation and source labels were checked.
- Independent Deep Kaz reviews approved the Apple/Android and audio changes.
  The audio graceful-drain correction was independently confirmed; the final broader
  audio review returned Approve with no substantive findings.
- Current changed/new source passed repository hygiene and whitespace checks.

## Release checks

- Versioned development and public builds passed signature and packaged permission/security audits.
- Development APK SHA-256: `35011f4e5e9e064c72a49137af9f4c72998069a3f65509198e890f8979492174`.
- Public APK SHA-256: `bac02d573596cffed1b7494228fa1495c16b11224bf74454890eb80f83a998f8`.
- Public certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.
- All 43 application payload entries match between the development/public APKs after
  excluding signature metadata. Only the manifest changed from the earlier reviewed
  implementation candidate; host suites apply to that same implementation.
- The exact versioned development APK and rebuilt instrumentation passed all **710
  API 37 checks** in the five modes listed above. Installed APK hashes were verified.
- All **116 release-signing assertions** passed using isolated generated credentials.
- The exact public APK passed **23 update/launch checks** on API 37: it updated the
  verified public 0.3.15 APK in place, retained its UID and all three existing runtime
  permission grants, matched installed bytes/version/code, and opened waiting for a
  user-started scan. No scan was started by the update test.
- A fresh independent **Deep Kaz review of the complete release changeset: Approve**,
  with no unresolved substantive findings. Documentation corrections were reviewed.
  Catalogue regeneration, repository hygiene and whitespace checks passed independently.
- The earlier broad-audio emulator initially stalled during Android startup; replacing
  only its isolated test data allowed the completed implementation matrix. The final
  versioned release matrix above passed on its fresh isolated emulator.

## Source and limits

Release assets include the pinned-public-signed APK, corresponding committed source,
`SHA256SUMS` and these notes. Source includes upstream notices, catalogue provenance
and required public regeneration inputs. Private keys, local caches and household
captures are excluded. No new permission, cloud recognition, persistent scan history
or background scanning is introduced.

Physical firmware compatibility and recognition accuracy require testing. No
household scan is claimed by the synthetic checks. API 26/36 and physical TalkBack
were not rerun. Unknown/withheld models remain unknown. Cast HTTP targets do not
establish member speakers or stereo/home-theater roles. HTTPS-only Cast metadata
and authenticated Cast membership are unsupported. Partial scans remain Partial.
The original Samsung missing-model cause remains unconfirmed; in-app update
checking is not included. Protocol limits: [Apple](../reference/APPLE-RECOGNITION.md),
[Android](../reference/ANDROID-RECOGNITION.md), [audio](../reference/AUDIO-ZONES.md).
