# Historical recog verification

These records apply to their named earlier candidates. See the current
[release verification](../VERIFICATION-0.3.13.md) and [status](../STATUS.md).

# Offline model recognition

Network Lens interprets the `model` TXT field of admitted current-wire
`_device-info._tcp` services using a small, pinned Rapid7 Recog subset. It adds no
network request, permission, cloud lookup, account, persistent history or runtime
dependency. Existing discovery work limits, network scope and the 55-second work
deadline remain in effect. A recognition match cannot establish presence, join
devices together or supply a hardware MAC address.

## Coverage and presentation

The generated table contains 156 exact canonical Apple model-code examples from
the retained upstream data: Mac notebooks/desktops, iPad, iPhone, HomePod, Apple TV
and iPod Touch. A code must be listed exactly; newer products and unsupported
families are not recognized by this Recog subset. The separate
[global product catalogue](../DEVICE-IDENTITIES.md) covers exact manufacturer/model
reports from worldwide hardware, including Android and IoT products. Neither
path establishes retail-model accuracy on physical hardware by itself.

The list puts the canonical product title first, adding the Apple brand where
needed, with a useful custom network name beneath it. Search includes interpreted
titles and device categories. Details label the interpretation **Locally recognized**,
preserve the reported code and keep the interpretation in a separate panel. Expanded discovery evidence
and explicit clipboard copies include the input protocol/field, rule number and
pinned upstream revision. Reported model, manufacturer, device type, firmware,
MAC and IEEE interface vendor are not rewritten. JSON has a separate
`localRecognition` object, including whether only a family is inferred.

Matching categories select Lucide laptop, desktop, tablet, smartphone, smart
speaker, TV media player or portable-player pictograms. This-phone, gateway and
specific HomeKit/surveillance roles retain their existing icon precedence.
Unknown or conflicting recognition retains the existing service/general icon
selection. An icon is a category interpretation, not an assertion of ownership,
authenticity or security.

## Admission and conflicts

- Recognition enters only through the current-wire device-info admission path.
  Native Android callbacks, cached records, other service types and routed proxy
  advertisements cannot seed this fingerprint input.
- Each endpoint keeps at most one input code, two distinct reported model values
  and a conflict flag. Input codes are bounded to 64 printable ASCII bytes and
  compared without trimming, case folding, prefix matching or sanitization into
  a known identifier. The DNS-SD parser folds key case and retains only the first
  occurrence of a duplicate key, as required by
  [RFC 6763 section 6.4](https://www.rfc-editor.org/rfc/rfc6763.html#section-6.4).
  Recognition evaluates that first value; later duplicate strings cannot replace
  an unsupported or malformed value with a known identifier. Raw packet tests
  exercise parsing, direct-service admission, assembly and recognition together.
- Different codes, including unsupported ones, suppress interpretation for the
  rest of that endpoint's scan. Conflicting specific reported model or
  manufacturer observations also suppress it, even if the report arrives before
  recognition or is later evicted from the eight-entry display-observation list.
  The flag stays set for the scan and is copied independently with snapshots.
  Two reported model values suffice because a match can agree only with its code
  and description; a third distinct specific value is conflicting. Reported values
  retain the existing 160-character observation limit. Recognition never guesses
  a missing manufacturer from an interface vendor or software name.
- Snapshot copies keep independent evidence. Group descriptions/icons require
  compatible endpoint evidence; endpoint details never borrow recognition from
  another address. New scans start with no recognition evidence, and cancelled
  discovery rejects later current-service updates.

## Data and updates

The upstream revision is `f1191e24332c51d6a3d98457f64fba8dec340784`.
The original XML and complete BSD-2-Clause terms are retained in
`android/recog-original/`. The full copyright, conditions and disclaimer are
also packaged as `res/raw/recog_license.txt`. Source and generated-output hashes,
upstream Git blob identities and exclusions are in
`android/recog-provenance.json`.

`scripts/generate-recog-models.py` selects only explicit canonical model-code
examples from fully anchored hardware rules and validates them against the
upstream pattern at generation time. It does not expand patterns to identifiers
that lack an example. Runtime matching uses a generated Kotlin exact lookup;
no upstream regex, Ruby runtime or general recognition engine runs in the app.

Board IDs, OS rules, broad family/prefix rules and the ambiguous `iMac10,1` /
`iMac11,1` descriptions are excluded. Apple TV rules carry upstream
`hw.certainty=0.10`; the selected entries expose only **Apple TV** and a TV media
player category, without a generation or a fabricated confidence percentage.

Nineteen further Mac identifiers are shared across years or screen sizes. Local
generation overrides retain only the common product family/details and mark
these entries `familyOnly`; exact code matching does not choose a retail variant.
For example, `MacBookAir7,2` no longer selects 2017 over 2015, `MacPro5,1` no longer
selects 2012 over 2010, and `iMac9,1` no longer selects a 24-inch screen over 20-inch.
The generated provenance records every override and the Apple reference pages
used to check [MacBook Air](https://support.apple.com/en-us/102869),
[MacBook Pro](https://support.apple.com/en-us/108052),
[Mac Pro](https://support.apple.com/en-us/102887),
[Mac mini](https://support.apple.com/en-us/102852) and
[iMac](https://support.apple.com/en-us/108054). Upstream files remain byte-for-byte
intact. Regression tests cover all 19 conservative descriptions.

Data refreshes are intentional maintenance changes: inspect the new upstream
content and license, update the pinned hashes and selection policy, regenerate,
and review the diff and tests. App builds never download updated rules.

```sh
python3 scripts/generate-recog-models.py --check
```

The host-test and direct app-build scripts run this check automatically.

## Current verification

See [release verification](../VERIFICATION-0.3.13.md) for current artifact hashes and
checks. The remaining records below describe their specific earlier candidates.

## Shared identifier correction verification (2026-11-14)

The follow-up review identified three further shared identifiers whose generated
descriptions selected an unsupported variant. The generator now retains these
common details and marks each entry `familyOnly=true`:

| Reported code | Conservative description |
| --- | --- |
| `MacBookPro15,1` | MacBook Pro (15-inch) |
| `MacBookPro15,2` | MacBook Pro (13-inch, Four Thunderbolt 3 ports) |
| `Macmini3,1` | Mac mini (2009) |

The new host regression failed against the prior table. After regeneration, all
23 host suites / 19,574 checks passed, including 3,365 Recog checks. The supported
direct app and instrumentation builds passed, along with packaged permission and
security audits. The retained upstream XML/license blob identities and packaged
license bytes remain unchanged. Coverage remains 156 codes, with 19 conservative
Mac descriptions and six family-only Apple TV descriptions.

The development APK SHA-256 is
`de9dbf1937bf65082bd0ae007f69aaed50e82bf26436062383fba9318d0d5641`;
the final instrumentation APK SHA-256 is
`97da4f1fb0c2900dac3aa46bbc06c037ec02bd1d2498792f5422ecf6663aec0f`.
The runner verified both installed APK hashes on each isolated emulator.

The focused `recog` mode passed 276 checks on each of API 36 and 37. Thirty-three
new assertions cover the three corrected codes through current-service admission,
JSON, list/accessibility text, the Device family label, reported-code preservation,
clipboard output, Back and narrow 200% text layouts. After inspecting the initial
273-check captures, the fixture was updated to let scrolling settle before capture
and check that each recognition heading remains fully visible. The final 276-check
runs passed. Synthetic MacBook Pro and Mac mini captures were visually inspected.

Both emulators booted with confirmed System UI ANR dialogs, which were closed
before app testing. The other five modes in the earlier six-mode matrices below
were not rerun for this table-only production correction. Real-device accuracy,
API 26 runtime behavior, physical TalkBack and whole-scan performance remain
unverified. No physical device was operated or release published; the existing
discovery limitations remain open.

## Review corrections verification (2026-11-13)

Three review corrections are committed separately: conservative descriptions for
16 shared identifiers (`64a462f`), scan-lifetime conflict tracking independent of
the display list (`d5903c0`), and raw DNS duplicate-key fixtures with corrected
documentation (`a1270c8`). The original upstream XML and license blobs remain
unchanged, and the packaged license matches the retained terms byte-for-byte.

All 23 host suites / 19,571 checks passed, including 3,362 Recog checks. The
supported direct app and instrumentation builds passed, as did the packaged
permission and security audits. That development APK SHA-256 is
`adf88ad73085472dffccadcef04df67411b1d978bff3351c5681001a0ba06a48`;
the instrumentation APK SHA-256 is
`bb46eb83a892990d1aada4f57c99ce976b09f69b9afc789c4e9b9d72c1bdc44c`.
Both installed binaries were hash-verified on each selected emulator.

| Earlier emulator matrix | Result |
| --- | --- |
| API 36: recog, local-metadata, typography, session-only, fresh-presence, navigation | All six passed; 1,222 checks |
| API 37: same six modes | All six passed; 1,222 checks |

Recognition contributes 243 checks per platform, including 31 new checks using
actual Device/IdentityHints state. They exercise model/manufacturer conflict
eviction, reports before and after recognition, snapshot independence, fresh
devices at the same address, equivalent reports and rejected/generic inputs.
Host packet fixtures cover duplicate key casing/order and malformed/unsupported
first values through both direct-service parsing and same-sender assembly.
Synthetic API 37 light list and API 36 dark 320dp/200% details screenshots were
visually inspected. The latter retained complete recognition-panel wrapping.

Before testing, API 36 again booted with a confirmed System UI ANR dialog.
It was closed on the isolated emulator and the full matrix then passed without
an application change. That APK is 1,932,192 bytes. No physical device
was operated or release published. Real-device coverage/accuracy, API 26 runtime
behavior, physical TalkBack use and whole-scan performance remain unverified.
The existing discovery limitations remain open.

## Initial implementation verification (2026-11-13)

The following artifact identities and counts describe the initial reviewed
implementation, before the subsequent review corrections.

The supported direct build and instrumentation companion build passed. Packaged
permission and security audits passed; the packaged Recog license was also
compared byte-for-byte with the retained upstream terms. All 23 host suites /
19,428 checks passed, including 3,219 Recog checks. The initial sandboxed host
run stopped at a prohibited loopback socket; the complete suite passed with
local fixture-socket access.

The final development APK SHA-256 is
`fc00c175c65845c7712c68c77385492426cb442a89b1299f83842849484b01e1`.
The instrumentation APK SHA-256 is
`80e7970a21cd74214153af4bab04ad74d0f392d3783017ca76b885c499f5eb81`.
Both installed APK hashes were verified on each selected emulator.

| Final emulator matrix | Result |
| --- | --- |
| API 36: recog, local-metadata, typography, session-only, fresh-presence, navigation | All six passed; 1,191 checks |
| API 37: same six modes | All six passed; 1,191 checks |

The recognition mode checks 212 assertions per platform, including a conflict
that remains suppressed even if an Apple manufacturer observation outranks a
contradictory report. It exercises light/dark, normal/200% text and 320dp/411dp
native layouts. Synthetic API 37 screenshots were visually inspected for list
icons, readable descriptions and narrow/large-text detail wrapping. Clipboard
copies preserve raw codes and separate rule provenance. Existing typography,
session, fresh-presence and navigation regressions also passed.

The first API 36 matrix failed several UI checks while an Android System UI ANR
dialog owned focus. The emulator window state and screenshot confirmed that
system dialog. After closing the crashed system component and verifying launcher
focus, the same app/test binaries passed the full matrix. This is recorded as an
environment failure, not a passing initial run or an application fix.

Compared with the pre-change development APK present in the workspace, the final
APK grew from 1,915,417 to 1,932,192 bytes: 16,775 bytes, about 16.4 KiB. The
uncompressed DEX increase was 32,920 bytes. A warmed host-only measurement ran
50,000 exact lookups in 15 ms; this is not a phone latency, heap-use or whole-scan
benchmark. Per-endpoint recognition state is bounded independently of packet
count, but retained heap overhead has not been measured on hardware.

No physical device was operated, no household scan was collected and no release
was published. Real-device model accuracy/coverage, Android API 26 runtime
behavior, physical TalkBack use and whole-scan performance remain unverified for
this increment. Existing Spotify availability and intermittent native service
resolution failures remain open. Offline interpretation does not resolve them
or convert a Partial scan into a Complete scan.
