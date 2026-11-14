# Offline model recognition

This unreleased increment interprets the `model` TXT field of admitted current-wire
`_device-info._tcp` services using a small, pinned Rapid7 Recog subset. It adds no
network request, permission, cloud lookup, account, persistent history or runtime
dependency. Existing discovery work limits, network scope and the 55-second work
deadline remain in effect. A recognition match cannot establish presence, join
devices together or supply a hardware MAC address.

## Coverage and presentation

The generated table contains 156 exact canonical Apple model-code examples from
the retained upstream data: Mac notebooks/desktops, iPad, iPhone, HomePod, Apple TV
and iPod Touch. A code must be listed exactly; newer products and unsupported
families remain unrecognized. This is not general Android/IoT recognition and
does not establish retail-model accuracy on hardware.

The list labels interpreted descriptions **Locally recognized** and allows
searching those descriptions and device categories. Details preserve the reported
code and show the interpretation in a separate panel. Expanded discovery evidence
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
- Each endpoint keeps at most one input code and a conflict flag. Values are
  bounded to 64 printable ASCII bytes and compared without trimming, case
  folding, prefix matching or sanitization into a known identifier. Duplicate
  case-insensitive `model` keys are ambiguous.
- Different codes, including unsupported ones, suppress interpretation for the
  rest of that endpoint's scan. Conflicting specific reported model or
  manufacturer observations also suppress it. Recognition never guesses a
  missing manufacturer from an interface vendor or software name.
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

Data refreshes are intentional maintenance changes: inspect the new upstream
content and license, update the pinned hashes and selection policy, regenerate,
and review the diff and tests. App builds never download updated rules.

```sh
python3 scripts/generate-recog-models.py --check
```

The host-test and direct app-build scripts run this check automatically.

## Verification (2026-11-13)

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
