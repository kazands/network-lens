# Offline model recognition

Network Lens interprets model fields admitted from reviewed current-wire Bonjour
services using pinned Rapid7 Recog and supplemental AppleDB tables. The tables
add no cloud lookup, account, persistent history, permission or runtime dependency.
Missing auxiliary model records may use the bounded local TXT queries described
below. Existing discovery work limits, network scope and the 55-second work
deadline remain in effect. A recognition match cannot establish presence, join
devices together or supply a hardware MAC address.

## Coverage and presentation

The generated table contains 156 exact canonical Apple model-code examples from
the retained upstream data: Mac notebooks/desktops, iPad, iPhone, HomePod, Apple TV
and iPod Touch. A supplemental AppleDB table adds **128 exact iPad/iPhone codes**
not present in that subset, including `iPad13,18` (10th-generation Wi-Fi),
`iPad13,19` (10th-generation Wi-Fi + Cellular), and `iPhone17,3` (iPhone 16).
The combined tables contain **284 codes**. A code must be listed exactly;
unsupported families are not recognized by these tables. The separate
[global product catalogue](DEVICE-IDENTITIES.md) covers exact manufacturer/model
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
speaker, TV media player or portable-player pictograms. This-phone and gateway roles retain precedence. Other physical type facts
are resolved together; contradictory types produce a conflict label instead of an
arbitrary winning icon. Unknown hardware retains a service/general icon.
See [the evidence resolver](RECOGNITION-EVIDENCE.md). An icon is a category interpretation, not an assertion of ownership,
authenticity or security.

## Admission and conflicts

- Recognition enters only through the reviewed current-wire model admission paths
  listed below. Native Android callbacks, cached records, unreviewed service types
  and routed proxy advertisements cannot seed these inputs.
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

## Verification

The current release's exact artifact hashes, checks and limits are recorded in
[release verification](VERIFICATION-0.3.15.md). The merged recognition and
catalogue feature passed all 26 host suites / 29,914 checks, including independent
review execution. The versioned download passed the 300-check API 37 identity
mode; earlier corrected-feature API 37 coverage also passed the 298-check Recog
mode. These counts describe their own candidates, not additional release checks.

Earlier parser, admission, family-description and native layout checks remain in
the [Recog verification history](archive/RECOG-VERIFICATION.md). Physical-device
model accuracy, unlisted hardware, physical TalkBack and phone performance remain
unverified for this release.

## Additional current Apple model sources

The same exact model-to-product mapping now accepts the reviewed current-wire
Companion Link `rpMd`, AirPlay `model`, and RAOP `am` fields. These adapted inputs
are labelled **Apple model catalogue**, with the actual service source and pinned
Recog mapping provenance; they are not presented as an observed device-info fingerprint.
Original device-info matches from the retained Recog table keep their Recog source
label. AppleDB matches instead identify AppleDB and its pinned source blob; no
Rapid7 rule is claimed for those entries. The original Recog table is unchanged.

Bonjour device-info may consist of a TXT record without a listening service. Such a
record can now qualify when a complete, unexpired service with the exact same
instance name was received from the same endpoint in this scan. Eligible anchors
are Companion Link, AirPlay, SMB and workstation services. A missing model triggers
at most two TXT questions under the existing shared follow-up budget. Proxy records,
native cached callbacks, unrelated instance names and expired records cannot qualify.
The auxiliary record never creates a listening port, MAC address or presence proof.

Companion Link without a reported model remains **Apple companion service**. The
app does not infer a hardware generation or manufacturer field from a user-chosen name.

## Supplemental Apple data maintenance

The complete public AppleDB device snapshot and MIT notice are retained in
`android/appledb-original/`, with upstream credits intact. Source Git blob
`22e79f5e8fa7cb79a53b7509450fec614dbc5877` was reviewed on 2026-11-15.
`scripts/generate-apple-models.py --check` verifies source and license hashes,
exact identifier syntax, release eligibility, unambiguous mappings and generated
output. Builds and host tests run this check offline. Existing Recog mappings
retain precedence, including conservative shared-model descriptions. New source
snapshots require explicit review and regeneration; the app does not contact
AppleDB. Exact code mapping alone does not prove the physical device reported
that code, nor does it establish Fing's recognition method.
