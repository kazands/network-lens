# SmartThings hub recognition

## Recognition contract

A qualifying current SmartThings advertisement now displays **SmartThings Hub**
with **Smart-home hub** as its physical type. A useful friendly name remains in
the subtitle. Technical hub identifiers remain available in endpoint observations
and search, rather than becoming the product name or subtitle.

Matter and SmartThings remain separate service capabilities. This change does not
claim that a LAN advertisement reports Zigbee or Z-Wave support, discover devices
behind those radios, or infer a hardware MAC from the hub identifier. Model,
manufacturer and MAC fields remain unknown unless actually reported.

## Evidence and boundaries

The rule requires all of the following in the same current mDNS service record:

- `_smartthings._tcp.` with a valid advertised port;
- a 16-digit hexadecimal service instance;
- a `hubv2-<identifier>.local` target whose identifier matches the instance,
  allowing the observed single leading zero in the hostname.

The hostname convention is an observed compatibility pattern, not a published
generation contract. It supports a hub-family interpretation; it does **not**
establish the retail generation. Other hostname patterns remain unsupported until
there is evidence for them. The rule is not tied to an IP, household identifier,
MAC prefix or particular TCP port.

Both `_smartthings._tcp.` and `_smartthings-hedge._tcp.` are explicitly queried
within the existing bounded mDNS schedule. The hedge service contributes a
SmartThings capability but does not establish hub hardware by itself. A SmartThings
service on a TV or appliance, a hostname alone, a cached native callback, or a
mismatched identifier cannot trigger the hub interpretation. Current-presence,
network scope, cancellation and grouping admission are unchanged.

Conflicting model or manufacturer evidence suppresses the product interpretation.
The hub constraint survives endpoint copies and IPv4/IPv6 grouping, so another
catalogue or grouped address cannot silently turn it into a speaker or phone.
A compatible bridge role can coexist with a hub; a television role conflicts.

## Research and provenance

Reviewed on 2026-11-18:

- [SmartThings support discussion](https://community.smartthings.com/t/smartthings-hub-is-no-longer-discoverable-using-mdns-jan-2024/277234):
  a V2 owner reports discovering the hub through the SmartThings mDNS service;
  SmartThings staff discuss the firmware-related discovery failure. This supports
  service use on hubs, not a universal hostname-to-generation mapping.
- [go-mdns Samsung parser documentation, v0.2.0](https://pkg.go.dev/gitlab.com/zygoon/go-mdns@v0.2.0/parsers/samsung):
  the project author reports this service on TVs and appliances and explicitly
  describes the TXT interpretation as observational. This is counter-evidence to
  treating every SmartThings service as a hub; it is not a vendor protocol spec.
- User-confirmed hub observations supplied the matching instance/hostname shape.
  Only independently created synthetic identifiers are used in repository tests.

This is original GPL-3.0-only implementation. No upstream code or database has
been incorporated. No new HTTP endpoint, login, hub command, cloud request or
radio permission is introduced.

## Verification

`SmartThingsInfoTest` covers bounded matching, mismatched and unrelated services,
technical-name presentation, capabilities, manufacturer compatibility, conflicts
and catalogue copies. The emulator-only `smartthings` mode, also included in
`device-identities`, exercises the actual current-record admission path, cached
callbacks, both Matter arrival orders, grouped addresses, preserved raw fields,
conflicts, own-device exclusion, Stop and fresh-scan isolation.

Local checks on 2026-11-18 passed all 46 host suites and both Python diagnostic
modules. The focused host suite passed 40 checks after the final explanation-copy
change. The API 37 emulator passed 32 focused SmartThings checks and 1,008 broader
`device-identities` checks before that copy-only change. Application and test APK
builds passed. The final application build, including the explanation text,
passed the 32 focused Android checks again. Installed APK hashes were verified
by the emulator runner. `git diff --check` and the repository hygiene check passed.

Physical hub verification and an exact-generation mapping remain outstanding.
See [current verification](../VERIFICATION-0.3.26.md) for the release artifacts and checks.
