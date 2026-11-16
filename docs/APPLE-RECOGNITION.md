# Apple recognition sources

This change adds a local, read-only Apple device-service lookup alongside Bonjour,
AirPlay, RAOP and Companion model fields. It is included in the 0.3.16 preview.
The existing 284-code catalogue is unchanged.

## Direct model lookup

A currently responding endpoint with observed or current-advertised TCP port 62078
can receive one lookup per scan. An earlier successful web or SSH port check does
not prevent the independent 62078 check. The scanner also browses
`_apple-mobdev2._tcp.`; native cached callbacks remain candidates, never models.
A port number or service name alone cannot identify Apple hardware.

On a fresh selected-Wi-Fi-bound connection, `QueryType` must return the exact
`com.apple.mobile.lockdown` type before a `GetValue` request for **ProductType**
is sent over that same connection. There is no pairing, trusted session, service
launch, device-name, serial, unique-ID, account, application-list or personal-content
request. Response fields never choose a destination or an additional request.

The reply must match the operation and field. A valid model code becomes a
source-labelled observation and enters the existing exact Apple catalogue.
Reported manufacturer and MAC stay unknown unless separately reported. Compatible
AirPlay/Companion/RAOP evidence combines with this model; contradictory model or
manufacturer reports continue to suppress exact identity. Model codes never merge
addresses or establish presence.

A denied/missing ProductType records **Apple service verified; model not provided**
and retains only an Apple device-service capability, not a guessed phone/tablet
or generation. Unknown codes stay raw model reports. Foreign services, malformed
responses and transport failures acquire no Apple identity. Endpoint lookup status
appears in expanded evidence and Copy device details; scan diagnostics report
failures and work limits.

## Bounds and compatibility

- At most 32 endpoint lookups per scan, using bounded metadata workers.
- Two requests maximum, two seconds total per lookup, within the scan deadline.
  No retry, redirect, authentication or downgrade path.
- Four-byte big-endian frames, each limited to 8 KiB before allocation. Only flat
  XML plist string dictionaries are supported. Duplicate keys, nested values,
  binary plists, unrelated requests, malformed UTF-8, unexpected DTDs and entities
  are rejected. The standard Apple plist DTD is inert and is never downloaded.
- Stop closes sockets and seals pending outcomes. Cancelled, replaced or
  out-of-scope endpoint responses cannot supply identity.
- Scan memory only; no database download, new permission, dependency, persistent
  inventory, analytics or cloud service.

HomePod, HomePod mini and Apple TV continue to use current AirPlay/RAOP and
Companion model fields. Their mappings select speaker or streaming-device icons
independently of capabilities. The direct service is an additional opportunity;
it is not assumed available on every Apple product. Existing family-only Apple TV
mappings remain family-only, without an invented generation.

## Protocol references

Inspected 2026-11-16. This is original Kotlin; no upstream library code or runtime
dependency is incorporated.

- [Apple network ports](https://support.apple.com/en-ca/103229): 62078 is used for
  pairing, synchronization and backup; port use alone is not identity.
- [QueryType documentation](https://docs.libimobiledevice.org/libimobiledevice/latest/lockdown_8h_a9e94b542d55e9b78dfe8a8dbbab88ef5.html),
  [Lockdown protocol](https://github.com/libimobiledevice/libimobiledevice/blob/master/src/lockdown.c)
  and [plist transport](https://github.com/libimobiledevice/libimobiledevice/blob/master/src/property_list_service.c):
  operation names, ProductType semantics and framing. Those LGPL implementations
  are protocol references, not bundled code.
- [pyatv protocols](https://pyatv.dev/documentation/protocols/): complementary
  Apple TV/HomePod advertisements.

## Regression coverage

`AppleDeviceInfoTest` covers minimal requests, malformed/duplicate/oversized frames,
fragmented loopback transport, foreign services, withheld models, timeout,
cancellation and catalogue conflicts. `AppleRecognitionChecks` runs under the
emulator-only `device-identities` mode, covering production admission, source
labels, icons, existing service evidence, cached candidates, limits, snapshots and
late responses after Stop. Fixtures use synthetic identifiers only. Physical
compatibility of a newly built APK remains separate from these checks and from
prior manual protocol diagnosis.

### Implementation verification — 2026-11-16

- All 28 host suites passed, including 70 Apple-specific checks for framing,
  parsing, catalogue integration, denied responses, in-flight cancellation and
  preserved permission/routing failure causes.
- The direct application and instrumentation builds passed, along with the
  application permission/security audits.
- The final rebuilt APK passed 937 checks on an isolated API 37 emulator:
  `device-identities` (516), `recog` (298), `local-metadata` (49),
  `scan-efficiency` (22), `fresh-presence` (31) and `resolver-upgrade` (21).
  Installed application and test APK hashes were verified by the runner.
- An independent Deep Kaz review approved the change after targeted confirmation
  of the transport exception-cause correction. No unresolved findings remain.

These implementation checks preceded release packaging and did not operate a
physical phone. They do not establish compatibility with every Apple device or
OS version. See [0.3.16 verification](VERIFICATION-0.3.16.md) for release checks.
Other Android API levels were not exercised for this change. The subsequent [Android/Cast changes](ANDROID-RECOGNITION.md) expand model
collection and category coverage; combined physical testing remains outstanding.
