# Local device metadata

The source includes the following additions after the 0.3.11 preview. These are
device-reported observations, not cloud recognition or authenticated hardware
identification. Every scan starts fresh and all metadata remains in memory.

| Service | Additional observations |
| --- | --- |
| Thread `_meshcop._udp` | `mn` model and `vn` reported vendor. Other Thread addresses and network identifiers are not interpreted as a hardware MAC. |
| ESPHome `_esphomelib._tcp` | Friendly name, firmware version and explicit MAC. Board and platform are labelled firmware-target details, not retail model/manufacturer. |
| Shelly `_shelly._tcp` | Read-only `/shelly` on the advertised port: model/type, firmware and explicit MAC. Gen1 `_http._tcp` advertisements with a Shelly instance/host form and exactly six or twelve hexadecimal ID digits also qualify. The ID is only a metadata-query marker, never a derived MAC. |
| WLED `_wled._tcp` | Advertised MAC plus `/json/info` for name, firmware, explicit MAC and platform. Firmware brand/product and Wi-Fi AP BSSID never supply hardware manufacturer/model/MAC. |
| Spotify Connect `_spotify-connect._tcp` | GET at the advertised `CPath` with the fixed `action=getInfo`: reported brand, model, name and device-type detail. Model is optional. No login, playback or account-changing action is generated. |
| IPP `_ipp._tcp` / `_ipps._tcp` | Get-Printer-Attributes at the advertised `rp` resource: `printer-make-and-model`. The value describes the print service, which can be a queue or virtual printer. It is not split into a guessed manufacturer. |

Only current wire service records can authorize these lookups. Cached Android
service callbacks remain candidates and cannot provide identity or authorize an
HTTP request. HTTP metadata additionally requires an already confirmed peer in
the selected Wi-Fi's direct eligible scope. Routed accessory advertisements do
not authorize HTTP. Sockets bind to the selected Android Network; scope and
network validity are checked before and after an exchange.

Requests go to the actual numeric peer and advertised port, never a hostname or
URL returned by the device. Spotify and IPP accept only bounded local paths,
without supplied queries, fragments, credentials or alternate authorities. There
are no redirects, guessed authentication credentials or secondary URL fetches.
IPPS uses platform TLS trust and endpoint verification, without a plaintext
fallback. Printers with untrusted certificates or required credentials can remain
unavailable. Successful metadata without an optional model leaves it unknown.

The existing 55-second scan-work deadline remains. HTTP metadata has two workers,
32 queued tasks, at most 32 attempts per protocol and 96 overall, with one attempt
per peer/protocol. Changing advertised paths/ports cannot add attempts. Each
exchange has a maximum two-second deadline within the remaining scan budget, a
400 ms connection timeout and reads bounded to at most 600 ms each. A socket
deadline also bounds TLS handshakes and writes. HTTP bodies are limited to 64 KiB;
JSON is limited to 32 KiB with depth/token/string limits. Only selected root JSON
fields or the correlated IPP printer-model attribute survive parsing.

Unavailable or invalid requested metadata is shown as a failed method; exhausted
queues/deadlines are incomplete methods. These states keep the scan Partial.
Stopping discovery closes sockets, shuts down the metadata lane/timer and rejects
late results. These additions do not establish a fix for the existing intermittent
HomeKit/HTTP Android resolution issue or measured coverage on real hardware.

## Development validation (2026-10-31)

The checks below validate bounded parsing and transport behavior. Physical-device
results and their remaining limits are recorded separately below. Keep the pull
request as a draft while the unavailable Spotify responses remain unexplained.

The current source, including the Gen1 short-ID correction, passed:

- `scripts/test.sh`: 20 suites, 14,131 checks, including 324 metadata checks and
  malformed-input/loopback HTTP/IPP/TLS fixtures. The new short-ID regression
  failed against the old matcher before the fix.
- `scripts/build.sh` and `scripts/build-android-tests.sh`: passed using full JDK
  21 and the pinned toolchain. Packaged permission/security audits passed; no
  permission or runtime library was added.
- API 36 `local-metadata`: 24 checks, with installed app/test APK hashes verified.
  This includes short-ID admission through a current advertisement without
  treating the ID as a MAC.
- Whitespace and repository hygiene checks, including changed/new working-tree
  files. Local logs and emulator artifacts remain ignored.

Current development APK SHA-256:
`0da7d67015642d4c903f102c3bdd59013310e487c40379bccad3622225ed5e40`.
Instrumentation APK SHA-256:
`8f53439b91378bb8a0bd23a292f59aede58c7111591d9686c6d81127deba64d7`.

Before the short-ID correction, the reviewed implementation also passed:

- 14 focused emulator suites: API 26 (133 checks), API 36 (91) and API 37 (80).
  These covered metadata, discovery/freshness, legacy resolution and API 37
  local-network permission behavior. They were not rerun on API 26/37 after the
  correction.
- 28 additional API 36 integration checks using real selected-Network-bound HTTP
  against a synthetic server on the emulator's host-loopback alias. All four HTTP
  protocols populated device details; authentication failure, cancellation after
  server receipt, socket cleanup and fresh-instance isolation were checked.
  Service/presence inputs were synthetic, so this was not real-device discovery.
- Matching-certificate IPPS success and mismatched-identity rejection in separate
  host JVM loopback fixtures. This does not establish trusted-IPPS success on
  Android hardware.

That earlier review used development APK SHA-256
`979975c1cc32ea7888503296c2017b70ce7f881bafa8a1f5cc70b317d78bf7e2`.
A fresh independent Deep review found one minor Gen1 short-ID compatibility gap;
the current source includes its correction and regression coverage.

## Physical-device validation (2026-10-31)

An explicitly authorized Pixel 8 running API 37 received an in-place update from
its existing production-signed preview to a candidate built from source commit
`505aa0e78fbbb04b933f3dd2010eeb74b8bcfbe3`. The candidate matched the installed
app's signing identity. Installed APK bytes matched the prepared candidate,
Android permission grants were preserved, and the updated process opened ready
with an empty result list and no automatic scan. The tested candidate remains
installed on that phone.

Signed candidate APK SHA-256:
`18e92c575fddf67a83d891583fb7286ce3fc45810557496d1a8c71145193e277`.
Its application ZIP payload matched the tested development APK, excluding signing
entries. Previous release outputs were preserved and restored byte for byte.
This candidate has not been published as a release.

| Check | Observed result |
| --- | --- |
| Existing installed preview: baseline foreground scan | Partial, about 36 seconds; mDNS type/hostname queries reached the 1,024-packet safety limit. |
| Candidate: first fresh foreground scan | Partial, about 37 seconds; some advertised Spotify metadata was unavailable. |
| Candidate: second fresh foreground scan | Partial, about 36 seconds; Spotify unavailability repeated, and native HTTP service resolution exhausted both bounded attempts without usable addresses. |
| Candidate: Stop scan during discovery | Terminal Partial observed after about 7 seconds and remained stopped. Complete per-method cancellation diagnostics were not captured. |

Times include UI observation overhead and are not engine benchmarks or evidence
of a performance improvement. Every method diagnostic was captured for the
baseline and both full candidate scans. The cancellation report remained intact,
but the helper could not reach its disclosure through the long report; only its
terminal Partial/stopped state was verified. Test-helper traversal/startup
adjustments do not establish application defects.

Both candidate scans accepted IPP printer information and Spotify Connect
metadata replies. Other Spotify metadata attempts were unavailable in both scans;
the generic diagnostic does not distinguish authentication, connection, response
or device behavior, so the cause is unresolved. Shelly and WLED metadata queries
were unused because no supported service was advertised. Successful mDNS query
status alone does not establish that Thread or ESPHome hardware was present or
that a retail model was identified correctly. Per-device field accuracy and source
labels were not checked on this phone.

The helper verified the specific authorized phone and focused-window owner before
and after UI operations, stopping on foreground loss. No instrumentation was
installed on the phone. Raw UI stayed in process memory; only local, ignored
aggregate evidence and fixed method diagnostics were retained. Public notes omit
household inventories and identifiers.

The following remain unverified for this candidate:

- Real Thread/ESPHome/Shelly/WLED compatibility and per-device model/source-label
  accuracy; broader device coverage and repeatable latency measurements.
- Trusted-IPPS success on Android hardware.
- Home/foreground-loss and Wi-Fi replacement transitions on this physical
  candidate; the guarded journey does not exercise those transitions.
- Full per-method cancellation diagnostics and a visual/TalkBack audit.

The existing intermittent HomeKit/HTTP resolution limitation remains open; HTTP
resolution failed again in the second candidate scan. Keep the PR draft pending
investigation of the unavailable Spotify metadata. Passing update, transport and
reporting checks does not make these Partial scans Complete.

## Protocol references

The additions independently implement documented wire behavior. No vendor code,
fingerprint database or additional runtime dependency is incorporated.

- [Thread Group border-router white paper](https://www.threadgroup.org/Portals/0/documents/support/ThreadBorderRouterWhitePaper_07192022_4001_1.pdf), section 9.
- [ESPHome mDNS implementation](https://github.com/esphome/esphome/blob/5f5a81434df4d47c3b9fb8d3927f11791a5bcbf6/esphome/components/mdns/mdns_component.cpp), TXT field definitions.
- [Shelly mDNS](https://shelly-api-docs.shelly.cloud/gen2/General/mDNS/), [Gen1 compatibility](https://shelly-api-docs.shelly.cloud/gen2/General/gen1Compatibility/) and [Gen1 `/shelly`](https://shelly-api-docs.shelly.cloud/gen1/#shelly).
- [WLED JSON API](https://kno.wled.ge/interfaces/json-api/) and [WLED mDNS advertisement](https://github.com/wled/WLED/blob/fce41f2655c3087d3eaf95c028d2856967cbc86e/wled00/wled.cpp).
- [Spotify ZeroConf API](https://developer.spotify.com/documentation/commercial-hardware/implementation/guides/zeroconf), Get Information.
- [RFC 8011](https://www.rfc-editor.org/rfc/rfc8011.html), sections 4.2.5 and 5.4.9; [PWG IPP guide](https://www.pwg.org/ipp/ippguide.html), printer discovery and resource paths.
