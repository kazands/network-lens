# Network Lens 0.3.13 verification

Verified locally on 2026-11-14 for the 0.3.13 development preview. This release
includes the merged canonical device names, global offline product catalogue
and PS5 discovery, including both reviewed response-order corrections.

## Identity and artifacts

- Application ID: `io.github.kazands.networklens`.
- Version: 0.3.13 / code 41; minimum API 26, target API 37.
- Development APK SHA-256: `ec966135581b2707cb1c3c96b74b231d3a516d4a7d25cda1ed3df278e1664ff0`.
- Public APK SHA-256: `56bce8e97693859fb2a18704250b761279ed68c508c1308bbf39801c5d88c36d`.
- Retained public signing certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

Both direct builds passed signature verification, permission allowlisting and
packaged security audits. Their 40 application ZIP entries match after excluding
signature metadata. Compared with the reviewed, tested feature APK, only
`AndroidManifest.xml` changed for the new version; application DEX, resources
and bundled catalogue bytes are identical. The pinned release key and recovery
receipt passed preflight. No signing identity was replaced or regenerated.

## Changes

Canonical product names lead the list, with useful custom network names beneath
them and source-labelled raw fields in Details. A pinned public hardware catalogue
covers 86,540 product/family titles across 5,263 brands through 256,338 exact,
manufacturer-qualified aliases. NIST hardware and MIT-licensed Android source
inputs, notices and reproducible generators are included. This is broad worldwide
coverage, not a complete inventory of every device or a guarantee of discovery.

Reviewed profiles include Sony PlayStation 5, NVIDIA SHIELD TV and the Hikvision
recorder family. Bounded, selected-Wi-Fi PS5 replies can supply current console
evidence. Broad matches require current manufacturer/model reports; IEEE interface
vendors and interpreted brands remain separate from reported manufacturer.
Equivalent aliases can arrive before the manufacturer/catalogue, and later console
corroboration can qualify an earlier ordinary PS5 model. Conflicts, overflow,
observation eviction, copies and fresh-scan isolation remain covered.
See [DEVICE-IDENTITIES.md](DEVICE-IDENTITIES.md) for precision, provenance and limits.

## Automated checks

- The merged feature and corrections passed all 26 host suites / 29,914 checks,
  including a fresh independent Deep review run. The final review approved both
  corrections without substantive findings. Those host suites were not rerun
  for the release metadata changes; application payload equality was verified.
- All 116 release-signing integration assertions passed with the 0.3.13
  development APK and isolated generated credentials.
- Direct development/public builds, instrumentation build, generated catalogue
  checks, signature verification and packaged permission/security audits passed.
- The versioned development APK passed all 300 device-identities checks on an
  isolated API 37 emulator. Installed app/test APK hashes were verified. Fixtures
  cover aliases, conflicts, snapshots, console admission, raw fields, titles/icons,
  search, copy, Back and text wrapping at 320dp/411dp, normal/200% text, both themes.
- The exact public-signed APK passed 21 API 37 update/launch checks. Public 0.3.12
  installed first; 0.3.13 updated it without uninstalling, retaining the UID and
  nonempty permission grants. Installed bytes, version/code, foreground branding
  and waiting for a user-started scan were verified.

The emulator initially displayed a confirmed System UI startup ANR, which was
closed before the successful app checks. The first public-upgrade verification
attempt successfully installed the update, then stopped because its UID reader
expected an older Android output field. The reader now uses the exact package's
`cmd package list packages -U` result; the full 21-check rerun passed. These were
test setup/reader corrections; the application APK bytes did not change.

Earlier feature matrices on API 26/36/37 and their exact candidate hashes are
recorded in the [implementation verification history](archive/IMPLEMENTATION-VERIFICATION.md). Their counts are not added to this version's
release checks. API 26/36 were not rerun for the version increment. No physical
device was operated for this release.

## Source and limits

Release assets include the public APK, corresponding committed project source,
`SHA256SUMS` and these notes. The source archive's `RELEASE-SOURCE.json` records
the commit/tree and per-file hashes. Public Kotlin standard-library sources and
IEEE regeneration inputs are retained alongside Recog, Lucide and catalogue
sources/notices. Local tools, credentials, private recovery records, household
captures and personal screenshots are excluded. Older release assets are retained.

Physical PS5 discovery, household identification accuracy, physical TalkBack,
phone performance and broader hardware compatibility remain unverified. The
catalogue is an offline snapshot; an online updater is not implemented. Existing
HomeKit/HTTP address-resolution limitations remain open. A Partial scan remains
Partial; this development preview does not establish Google Play production readiness.
