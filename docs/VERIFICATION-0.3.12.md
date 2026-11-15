# Network Lens 0.3.12 verification

Historical verification record for the artifact named below. For the current
download and functionality, see [release verification](VERIFICATION-0.3.14.md)
and [development status](STATUS.md).

Verified locally on 2026-11-14 for the 0.3.12 development preview. This release
includes the merged local metadata, mDNS diagnostics and offline Recog changes.
It remains a foreground, user-started Wi-Fi scanner with in-memory results only.

## Identity and artifacts

- Application ID: `io.github.kazands.networklens`.
- Version: 0.3.12 / code 40; minimum API 26, target API 37.
- Development APK SHA-256: `221d006650ea43a9268c717291c017b1a371af6a467678fe5b0ba0becf8c0e9e`.
- Public APK SHA-256: `1d4690622cb2631af84a07e0a914a63b2fa1c500ded320ed75667bcdf62693ec`.
- Retained public signing certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

Both direct builds passed signature verification, permission allowlisting and
packaged security audits. All 35 application ZIP entries match between the
development and public APKs after excluding signature metadata. Compared with
the tested final Recog candidate, only `AndroidManifest.xml` changed for the new
version; application DEX and resources are byte-identical. No key was replaced
or regenerated. The release key and its recovery receipt passed preflight.

## Changes

Current local service responses can supply source-labelled Thread/ESPHome fields
and bounded Shelly, WLED, Spotify Connect and IPP metadata. Details distinguish
lookup outcomes and other observations supplying the same endpoint's fields.
mDNS traffic and current-question suppression remain bounded, with diagnostics
for native service resolution versus captured current-wire records. See
[LOCAL-METADATA.md](LOCAL-METADATA.md) for exact historical checks and limits.

Offline Recog interprets 156 listed Apple model codes from admitted current mDNS
device-info records. Interpretations, input/rule provenance and category icons
remain separate from reported fields. Nineteen shared Mac identifiers and six
Apple TV codes retain conservative family descriptions. Conflicts remain
suppressed for the scan, including after display-observation eviction. Native
cached callbacks cannot authorize recognition. See [RECOG.md](RECOG.md).

## Automated checks

- All 23 host suites / 19,574 checks passed, including 3,365 Recog checks.
- All 116 release-signing integration assertions passed using the versioned
  development APK and isolated generated credentials.
- Direct development/public builds and the instrumentation build passed.
- The versioned development APK passed the 276-check recognition suite on a
  fresh API 37 emulator, with installed app/test hashes verified. Fixtures cover
  current-service admission, conflicts, snapshot/fresh-scan isolation, grouping,
  icons, reported fields, family labels, search, clipboard, Back and native text
  layouts in both appearances at 320dp/411dp and normal/200% text.
- The exact public-signed APK passed 15 API 37 upgrade/launch checks. Published
  public 0.3.11 installed first, then 0.3.12 updated it without uninstalling.
  Installed bytes, version/code and preserved nonempty runtime grants were
  verified. The public app launched with Network Lens branding and waited for a
  user-started scan.

Earlier API 36/37 recognition runs and the broader six-mode matrices in
[RECOG.md](RECOG.md) describe their own earlier candidates; their counts are not
added to these release checks. The new release version was not rerun on API 26
or API 36. No physical device was operated for this release verification.

## Source and limits

The release includes the public APK, corresponding committed source ZIP,
`SHA256SUMS` and these notes. `RELEASE-SOURCE.json` records the exact commit/tree
and per-file hashes. Original Recog data and notices, Lucide sources/notices,
the public IEEE snapshot inputs and Kotlin standard-library source are retained
for source attribution and reproduction. Local tools, signing material,
household data and personal screenshots are excluded. Older release assets and
tags are retained.

Real-device recognition accuracy, unlisted hardware, trusted IPPS on physical
hardware, physical TalkBack and whole-scan performance remain unverified.
Existing unavailable Spotify endpoints and intermittent native HomeKit/HTTP
resolution failures remain open. These changes do not turn a Partial scan into
a Complete scan or establish production readiness on Google Play.
