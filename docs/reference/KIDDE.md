# Kidde smart detector recognition

A matching current advertisement identifies **Kidde Smart Detector**, with a
**Safety detector** subtitle and Lucide's `alarm-smoke` icon. The pictogram does not
establish reported smoke/CO capabilities. This is a WD-family
interpretation, not an exact retail-model or smoke/CO capability claim. The raw
reported model remains `WD`; missing manufacturer and MAC fields remain unknown.
Useful friendly names remain visible, while technical Kidde identifiers stay in
technical details and search.

## Matching boundary

The same directly received, current mDNS service must contain:

- `_hap._tcp.` and a valid SRV port;
- an instance matching `Kidde-WD-<12 hexadecimal characters>`;
- a matching `<instance>.local` SRV hostname;
- exactly one case-insensitive `md` TXT key with the two-byte value `WD`.

DNS case and a hostname's final dot are normalised. Arbitrary `WD` model strings,
HTTP advertisements alone, mismatched identifiers, native cached callbacks and
Wi-Fi proxy advertisements do not establish this identity. Existing same-sender
PTR/SRV/TXT/address assembly, TTL expiry, network scope and Stop gates remain in
force. The identifier is not converted to a hardware MAC. Serial-number TXT
values remain withheld by the diagnostic allowlist.

The family constraint survives copies and endpoint grouping. Conflicting models
or manufacturers suppress the product match and prevent another catalogue from
silently reclassifying it as a phone or speaker. This conservative rule does not
resolve additional retail-model aliases; those require separately verified rules.

The `_hap._tcp.` record is retained, but its capability label is **HomeKit
advertisement** for endpoints with the Kidde fingerprint. Details explicitly say
that compatibility has not been verified. An advertisement alone does not prove
that Apple Home pairing or a working HomeKit endpoint is available.

## Research and limits

Reviewed 2026-11-19:

- [Kidde P4010ACSCO-WF product documentation](https://www.kidde.com/products/smart/smoke-and-carbon-monoxide-alarm-with-smart-features)
  and its linked [user guide](https://brand.kidde.com/m/58ce8a5466c86f54/original/P4010ACSCO-WF_UG.pdf)
  establish that this retail model is a smoke-and-CO alarm. They do not establish
  a universal `WD`-to-retail-model mapping.
- [Kidde HomeSafe client, commit 6e5843a](https://github.com/865charlesw/kidde-homesafe/tree/6e5843a5bd04bbb9fd14f5be100fa0e257f5ab0a)
  obtains richer device data through authenticated cloud requests. Its example
  separates `WD-ESP32` hardware from model and sensor capabilities.
- [Home Assistant integration, commit 9785e77](https://github.com/schmittx/home-assistant-kidde/blob/9785e77630377f6de37196b462c98a06c0cebbb8/custom_components/kidde/api/__init__.py)
  also uses the authenticated Kidde cloud API. It is not a public local reader.
- Direct read-only observations supplied the fingerprint shape above. The tested
  device advertised HTTP and HAP endpoints, but both advertised TCP ports refused
  connections at the time. This does not establish permanent unavailability or
  rule out a reader on another firmware. Household identifiers are not retained
  in the repository; fixtures are independently generated.

The fingerprint supports a Kidde detector-family inference with limited physical
validation, not coverage of all Kidde models. No cloud login, new network probe,
alarm command, permission or external dependency is added. No upstream code or
recognition database was copied; this is original GPL-3.0-only implementation.

## Regression coverage

`KiddeInfoTest` covers matching, malformed/ambiguous fields, wrong services,
conflicts, presentation, copies/grouping, current wire assembly, partial and
expired records, wrong senders, and serial withholding. The emulator-only `kidde`
mode (also part of `device-identities`) checks Discovery integration, both HTTP/HAP
arrival orders, icons, reported fields, grouping, native cached callbacks, own-phone
exclusion, conflicting models, Apple fallback, cancellation and fresh-scan isolation.

Physical verification of the updated APK remains separate from the development
machine's read-only discovery checks. Exact retail-model detection remains unsupported.

### Implementation-candidate checks — 2026-11-19

- All 52 host suites and Python diagnostic tests passed; `KiddeInfoTest` passed
  69 checks, including raw duplicate-model packets in both orders.
- Direct application and instrumentation builds passed. Existing Android/JDK
  deprecation and compatibility warnings remain.
- API 37 emulator checks passed: `kidde` 39, `device-identities` 1,277,
  `homekit` 69, `split-mdns` 24, and `recognition-report` 235 (1,644 assertions).
  The runner verified the installed application hash. The development APK still
  carries version 0.3.29. These candidate results are separate from the
  [versioned 0.3.30 release checks](../archive/VERIFICATION-0.3.30.md).
- An initial emulator startup failure occurred before app tests. After restart,
  a mixed-case grouping link in the new synthetic fixture caused two modes to
  stop; the fixture was corrected to use discovery's canonical lowercase link,
  and both modes passed on rerun. No production grouping change was needed.
- Deep independent review identified two minor issues: duplicate model-key
  information lost during decoding, and an unqualified HomeKit label restored by
  a grouped proxy endpoint. Both were fixed with regressions; the reviewer
  independently reproduced their resolution and approved the targeted fixes.
- Whitespace and repository hygiene checks passed, including the changed and new
  working-tree files. No physical-phone installation or updated-alarm scan was
  performed.
