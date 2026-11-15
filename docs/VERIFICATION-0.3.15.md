# Network Lens 0.3.15 verification

Release checks performed locally on 2026-11-15. This is an Android development
preview; physical-device recognition accuracy remains unverified.

## Identity and artifacts

- Application ID: `io.github.kazands.networklens`.
- Version: 0.3.15 / code 43; minimum API 26, target API 37.
- Development APK SHA-256: `c977a45bb5cbe1fff03d096d7be1f6c60826499500766f86952868a5047d28a5`.
- Public APK SHA-256: `fad99155eae297406c0006bd2ae263c7bbd7d8c9c185592d6d9a4c1f88846d42`.
- Pinned public signing certificate SHA-256:
  `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

Both direct builds passed signature verification and packaged permission/security
audits. Their 41 application ZIP entries match after excluding signature metadata.
Only the manifest differs from the previously reviewed development candidate in
[recognition evidence](RECOGNITION-EVIDENCE.md). The existing public key and backup
receipt passed preflight; no signing identity was replaced or regenerated.

## Changes

- Resolve physical device type independently of audio, casting and other services.
  Ecobee Premium's reviewed manufacturer/model aliases now agree across Spotify,
  AirPlay and grouped IPv4/IPv6 reports. Supported thermostats retain their type
  and icon even when media services are present.
- Preserve conflicting physical-type evidence and keep reported fields separate
  from catalogue interpretations. Details, search and copying expose the evidence
  and service capabilities.
- Accept guarded Samsung extensionless UPnP description URLs, interpret explicit
  television descriptions, and recognize the qualified PN59D550 model family.
  Description failures remain visible. The cause of the user's earlier missing
  Samsung model was not confirmed.
- Interpret reviewed current Companion Link, AirPlay and RAOP model fields.
  Associate auxiliary Bonjour model records only with complete current services
  from the same sender and exact raw instance name. Bounded queries preserve raw
  DNS labels, including whitespace, without terminating multicast discovery.
- Add 128 exact, released iPad/iPhone identifiers from pinned MIT-licensed AppleDB
  facts to the existing 156 Recog identifiers. Unknown or withheld models remain
  unknown; an Apple service alone does not establish a hardware generation.
- Continue bounded optional TCP checks after a refused port. Fresh-response
  admission, selected-Wi-Fi scope, cancellation and scan deadlines remain.

The global catalogue remains at 256,338 qualified aliases, 86,540 product/family
titles and 5,263 brand keys. There is no cloud recognition, saved scan history,
background scan, new permission or runtime catalogue download.

## Checks

- A fresh independent Deep Kaz review covered the complete implementation and
  returned **Approve**, with no substantive findings. It independently compiled
  and passed all **27 host suites / 30,568 checks**, including generated-data checks.
- The DNS-label regression failed against the previous implementation and passed
  after correction. Cases cover raw whitespace, leading/trailing spaces, dots,
  UTF-8, IPv4/IPv6, all four supported anchor types and wrong-instance rejection.
- Versioned development/public builds and instrumentation build passed.
- All **116 signing integration assertions** passed using isolated generated credentials.
- The exact versioned development APK passed **964 Android checks** on an isolated
  API 37 emulator: split-mdns (24), fresh-presence (31), resolver-upgrade (21),
  device-identities (472), recog (298), homekit (69) and local-metadata (49).
  Installed application and test APK hashes were verified. Presentation coverage
  includes 320dp/411dp, normal/200% text, light/dark, search, copy and Back.
- The exact public-signed APK passed **23 update/launch checks** on API 37. It
  updated the verified public 0.3.14 APK without uninstalling, retaining its UID
  and all three established runtime permission grants. Installed bytes, pinned
  certificate, version/code, foreground branding and waiting for a user-started
  scan were verified. No scan was started by the update check.
- The independent release-metadata follow-up approved version wiring, payload
  equality, signing identity and accurate scope/limits. Documentation links and
  whitespace checks passed for original project changes. Four trailing-whitespace
  lines in the retained upstream AppleDB README are preserved verbatim.

Host suites were run against the reviewed implementation before the release
version increment. Application payload comparison verifies that only the manifest
changed. Versioned Android checks are recorded separately above.

## Source and limits

Release assets include the public-signed APK, corresponding committed project
source, `SHA256SUMS` and these notes. `RELEASE-SOURCE.json` records the source
commit/tree and per-file hashes. Public IEEE regeneration inputs, Kotlin sources,
catalogue provenance and upstream notices are retained. Private signing files,
recovery records, local tools and household data are excluded.

API 26/36, physical TalkBack, phone performance and fresh recognition of the user's
Ecobee, Samsung TV and Apple devices remain unverified for this candidate. The
catalogue cannot recover identity that a device does not disclose. Existing
discovery/metadata limits remain, and Partial scans remain Partial. In-app update
checking is a separate, unimplemented task. See [status](STATUS.md).
