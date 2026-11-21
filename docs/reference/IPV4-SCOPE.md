# IPv4 discovery scope

Network Lens discovers responding IPv4 endpoints on the selected Wi-Fi and eligible IPv4 range. It does not directly scan IPv6 endpoints or follow routes to Thread accessories. A Wi-Fi connection without an IPv4 address remains connected in the interface, but Scan is disabled with an explicit IPv4 requirement. Direct scan callers receive the same requirement, not an empty successful scan.

The IPv6 mDNS and SSDP transports, ICMPv6 transport and exchange, routed accessory graph, route admission, per-owner IPv6 admission, and IPv6 worker lane have been removed. Retry, cancellation, selected-network binding, current-response requirements and work limits continue to apply to IPv4 discovery. Native Android service discovery can report candidates from either family. An AAAA-only update remains pending within the original four-second resolution deadline, allowing a later eligible IPv4 address to enter normal confirmation and the existing one-second settling period. A service still reporting only IPv6 at the end of its window is excluded without retry, presence or identity. Empty or off-scope IPv4 updates retain normal failure handling; updates from another network cannot establish an exclusion. Android manages its own resolver traffic; this policy describes app-owned transports and admitted endpoints, not every packet emitted by the operating system.

IPv4 mDNS replies can contain A and AAAA records together. Decoding those records does not admit an IPv6 endpoint or establish presence for another address. General address parsing, bounded metadata readers and report formatting remain defensive about IPv6 literals. In particular, IPv6 redaction remains necessary because IPv4 devices can return IPv6 addresses in text. Normal platform networking for HTTPS updates is unchanged.

## Coverage tradeoff

IPv6-only devices, including eligible Matter/Thread endpoints reached through a border router, are outside this inventory. An IPv4 responder can still advertise Matter or HomeKit services; that does not establish the presence of its IPv6-only children. A model or name learned only from an IPv6 responder will no longer enrich a grouped IPv4 endpoint. No claim is made that the narrowed scan finds the same devices or identity information on a household network.

## Comparison and validation

Before removal, an internal immutable policy compared the real Discovery admission and mDNS identity paths with the same synthetic inputs on Android API 37. All 16 comparison checks passed. The dual-stack fixture retained six endpoints; IPv4-only retained three. Every retained IPv4 name, model, manufacturer, service and port matched the baseline. The fixture also demonstrated loss of IPv6-only presence and loss of a model available only through an IPv6 responder in an otherwise IPv4-addressed group. Cached native callbacks and post-cancellation packets did not supply presence.

The temporary dual-stack policy was then removed. Comparison APKs, source, exact hashes and logs remain in ignored local evidence directories. No household data was copied into tests or documentation. This was an in-process synthetic comparison, not a live packet capture, performance benchmark or paired household scan.

Initial validation completed on 2026-11-21 using the [documented direct build path](../BUILDING.md), before the subsequent native callback ordering fix:

- All 55 host suites, Python diagnostic tests and generated catalogue checks passed.
- The development APK build, packaged permission audit and security audit passed.
- API 37: all 15 selected modes passed, totalling 2,322 assertions. These cover IPv4 scope, local/public metadata, device identities, split mDNS, retries, ownership, freshness/recovery, engine behavior, Compose interface, network changes, reports, ICMP and the Android local-network permission flow.
- API 26: all five selected modes passed, totalling 399 assertions: IPv4 scope, split mDNS, retries, ownership and local metadata.
- Recognition coverage, repository-content checks and `git diff --check` passed.

The emulator runner verified installed app and test APK hashes. Local evidence is in `artifacts/local-ipv4-comparison/`, `artifacts/local-ipv4-final37/`, `artifacts/local-ipv4-final26/` and `.tools/ipv4-transition/`; these directories are ignored. Exact SHA-256 values:

| Binary | SHA-256 |
| --- | --- |
| Temporary A/B comparison app | `3048a734958a6516f43f560068da74b0f68ec2574591badc48ad1ebff1fc5949` |
| Temporary A/B comparison tests | `de0e39fb73c7c28d9a0bbe520889dd20b221553cefddae7cfc187a19fb9bbbd0` |
| Initial IPv4 app tested on both APIs | `cd2605d3a27e321932a8e9b2bcfbe29af97235eb765d7dfc4f2e3d42bcb84abc` |
| API 37 tests | `75d3e587c95188e49793d0ff663cb52d153ebd0fb6f3b194606059e137d75bb0` |
| API 26 tests | `18eeaba1605901408d815a60787fad479c6281b0aea0710de553fcabf248bab1` |

Only the isolated API 26 and API 37 emulators were operated. Assertions measure synthetic behavior, not product coverage or household discovery accuracy. Physical before/after discovery quality and timing remain unverified.

### Native callback ordering correction

The new AAAA-first/A-later regression failed against the initial IPv4 APK before the fix, then passed after exclusion was deferred to the end of the native resolution window. Its fixtures also cover permanently IPv6-only services, repeated and late updates, cached identity/presence rejection, empty and off-scope IPv4 updates, another-network updates and Stop. Fixtures drive the production window-expiry boundary directly; they do not sleep through the timeout or register a live NSD service.

The corrected app and instrumentation builds passed, as did the packaged permission and security audits. All six selected API 37 modes passed with 429 assertions: `ipv4-scope` (64), `service-retries` (213), `resolver-upgrade` (23), `engine-upgrade` (16), `scan-ownership` (21) and `freshness-fixes` (92). All three selected API 26 modes passed with 278 assertions: `ipv4-scope` (47), `service-retries` (210) and `scan-ownership` (21). Shared host sources were unchanged by this correction; all 55 host suites had passed during review and were not rerun for this Android-only fix. Repository-content and whitespace checks passed again.

Both emulator matrices verified the installed APK hashes. Evidence is in ignored `artifacts/local-ipv4-review-red37/`, `artifacts/local-ipv4-review-fixed37/`, `artifacts/local-ipv4-review-fixed26/` and `.tools/ipv4-review-fix/` directories.

| Corrected binary | SHA-256 |
| --- | --- |
| App tested on both APIs | `054a189fe36f3ff4f66f8662f2ca27a5798046e183c37b616b2af865f1a55767` |
| Tests used on both APIs | `cc9b9f78717363eea16e36f803feeb367395982af0d2da0790487b667b9e099b` |

The subsequent Scan range help correction replaces the outdated IPv6 discovery promise with “Network Lens scans IPv4 devices only.” Its direct development build, packaged permission/security audits and whitespace checks passed. APK SHA-256: `bedfa8b55c31864117140997a510fc2fd0662771b3a607a0b03b8b942151f22e`. Instrumentation was not rerun for this wording-only change; the native callback matrices above describe the earlier binary, preserved locally as `.tools/ipv4-review-fix/before-range-copy.apk`.

Pre-PR host verification completed with the documented `bash scripts/test.sh` runner and local-socket access. All 55 host suites, Python diagnostic tests and generated catalogue checks passed, including the eight loopback suites previously blocked by the sandbox. The complete log is retained locally at `.tools/ipv4-review-fix/pre-pr-host.log`. This closes the host-test verification gap; physical-network coverage and timing remain unverified.

## Rollback

Reverting the IPv4 scope change restores the earlier dual-stack implementation. There is no stored inventory or preference migration: scans and settings are session-local. Historical verification documents describe their original builds and may still mention removed IPv6 tests.
