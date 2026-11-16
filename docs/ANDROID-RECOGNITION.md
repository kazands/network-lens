# Android and Cast recognition

These changes accompany [Apple service recognition](APPLE-RECOGNITION.md). They
are included in the 0.3.16 preview. Physical testing of the combined APK is still
required; synthetic checks do not establish household discovery coverage.

## Collecting current device information

A current Cast receiver advertisement can trigger one bounded, selected-Wi-Fi
HTTP request per endpoint and scan, including when a useful name is already known:
`GET /setup/eureka_info?params=device_info,name` on port 8008. The advertised
playback port and any supplied URL/path cannot redirect this request.

The strict JSON parser retains only root `name` and nested `device_info.name`,
`device_info.model_name` and `device_info.manufacturer`. It rejects duplicate
keys, invalid UTF-8 and malformed JSON, and limits body size, depth, tokens and
field lengths. Array members and dotted keys cannot impersonate object paths.
Models are not truncated into a different catalogue key. Claimed addresses,
serials, network names, account information, location and group-member lists are
not retained or followed.

The resulting model/manufacturer observations use the existing conflict-aware
catalogue. A useful current name stays selected while the alternative Cast name
remains source-labelled. There is no assumption that every Cast receiver is made
by Google, is an Android device, or is a television rather than a speaker or box.
The reviewed `Google Inc.` corporate spelling normalizes for catalogue matching;
the reported manufacturer remains unchanged.

Cast uses the common metadata outcome model, with explicit missing fields,
connection/HTTP failures, cancellation and work-limit reporting. Its independent
two-worker lane is retained. The shared admission limit is 32 per method and 96
total metadata lookups; each exchange has a two-second deadline within the scan
deadline. Cached native callbacks cannot trigger identity. Stop, lost presence,
scope changes and replaced endpoint objects reject late results.

The fixed lookup currently supports HTTP on port 8008. HTTPS-only endpoints,
authentication requirements and devices that expose no usable local model remain
unsupported by this lookup. No certificate-verification bypass, pairing, remote
control or developer-mode access has been added. Existing guarded UPnP, Spotify
and current mDNS reports remain complementary sources.

`_androidtvremote2._tcp.` now receives an explicit browse alongside service-type
enumeration. It contributes an Android TV remote-service capability, not a guessed
physical television category. The app does not pair with or control that service.

## Physical categories from a separate public source

The pinned LineageOS Wiki source supplies physical types, cross-checked against
the existing Google-validated Android naming table. The resulting separate
CC BY-SA 3.0 asset has **651 manufacturer/model pairs across 24 brands**:
607 phones, 43 tablets and one streaming device. This is additional category
coverage; it does not replace or reduce the existing global naming catalogue.

Only exact reported manufacturer/model pairs can use these categories. The
generator uses explicit upstream model lists when available; otherwise it joins
an exact manufacturer and marketing name to Google's model codes offline.
Runtime names, device codenames, MAC vendors, prefixes and substrings never
establish a match. Unsupported types, ambiguous titles and conflicting categories
are excluded. Unknown categories remain unknown, and contradictory observed
manufacturers continue to suppress interpretation.

The source projection retains each upstream path and original file SHA-256.
`android/android-hardware-provenance.json` records revision, licence, filters,
hashes and counts. `scripts/generate-android-hardware.py --check` reproduces the
asset offline. The source declaration and full attribution licence are retained
under `android/lineage-original/`; the notice and licence are also packaged in
the APK. This data remains CC BY-SA 3.0 and does not relicense application code.

## Multi-zone audio and playback groups

A Cast advertisement explicitly reporting `Google Cast Group` or the multizone
group capability bit is recorded as a **speaker group service**. Its name is
retained in discovery details, but its model, hostname and group ID do not supply
hardware identity, join different hosts, or initiate a physical-device metadata
lookup. A physical receiver advertised on the same address can still be
recognized independently. A group-shaped metadata response likewise supplies
only a group role, not a hardware product.

This prevents the new Cast path from treating a playback group as a physical
speaker. It does not implement Sonos group topology or a VSSL zone-to-chassis map.
Multi-zone amplifiers may expose several network endpoints; association requires
current device evidence. Matching room names or participation in one playback
group is not proof that endpoints are one physical device.

## References and tests

Protocol references inspected 2026-11-16; implementation is original Kotlin:

- [PyChromecast information requests and response fields](https://github.com/home-assistant-libs/pychromecast/blob/master/pychromecast/dial.py)
  and [Cast capability definitions](https://github.com/home-assistant-libs/pychromecast/blob/master/pychromecast/const.py).
  No PyChromecast implementation or model database is bundled.
- [Pinned LineageOS device facts](https://github.com/LineageOS/lineage_wiki/tree/09e2176e2693dca067e9ce803ea1a5eb909806c3/_data/devices),
  with source attribution and transformation details in the provenance file.
- [VSSL A.6x](https://www.vssl.com/vssl-products/a6x) and
  [Sonos system concepts](https://docs.sonos.com/docs/how-sonos-works) distinguish
  multi-zone hardware, players and logical playback groups.

`AndroidRecognitionTest` covers nested-field selection, malformed data, private
field exclusion, fixed requests, group discrimination, category qualification,
conflicts and loopback HTTP responses. Emulator-only `AndroidRecognitionChecks`
exercises named-receiver admission, native-cache exclusion, current reports,
snapshots, group/receiver coexistence, non-merging group IDs, categories/icons,
missing fields, replaced occupants and Stop. Fixtures are synthetic; no household
captures or device identifiers are included.

## Combined verification — 2026-11-16

- All **29 host suites** passed, including **70 Apple** and **56 Android/Cast**
  recognition checks. The pinned category generator reproduced all 651 rows.
- Direct application and instrumentation builds passed. Application permission
  and security audits passed. The emulator runner verified installed APK hashes.
- On isolated API 37, seven modes passed initially: `device-identities` (544),
  `recog` (298), `local-metadata` (49), `scan-efficiency` (22), `fresh-presence`
  (31), `resolver-upgrade` (21), and `grouping` (33): **998 checks**.
- The initial eight-mode matrix was **FAIL**, because `engine-upgrade` failed
  its existing IPv6 multicast hostname assertion. That mode passed **16 checks**
  on an isolated rerun with identical application and test APK hashes. Thus
  **1,014 checks passed across those runs**, not in one clean initial matrix.
  Competing emulator advertisements are a plausible explanation, but the actual
  cause was not proved. The fixture and reverse-PTR admission path are unchanged
  by this implementation. This intermittent test remains a verification limit.
- Independent full-scope **Deep kaz-code-review: Approve**, with no substantive
  findings. A targeted review of the failed/rerun evidence left that verdict
  unchanged and confirmed the reporting limitation above.
- Whitespace and working-tree path/common-credential hygiene checks passed.

These checks preceded release packaging and did not operate a physical device.
Other Android API levels and physical accuracy remain unverified. Subsequent
[audio checks](AUDIO-ZONES.md) cover synthetic VSSL/Sonos and broader audio
relationships; [0.3.16 verification](VERIFICATION-0.3.16.md) records the final
release checks. The HTTP-only Cast boundary above still applies.
