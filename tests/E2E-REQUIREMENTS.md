# Network Lens acceptance requirements

This checklist records the focused app agreed in the conversation. Results belong to the APK hash and test environment in the versioned E2E report; this file does not itself claim a pass.

| ID | Agreed requirement | Acceptance evidence |
| --- | --- | --- |
| R01 | Use Network Lens in the app and Network Lens: Network Scanner for the planned Google Play title; keep a simple scan/list/details experience. | Launcher/package label; first launch; no account, dashboard, extra tools, monitoring, alerts or background components. |
| R02 | Find responding devices on the connected Wi-Fi. | User-triggered real self and non-self gateway discovery, incremental results, ICMP-first checks with one spaced retry, two-connection TCP fallback with a cancellable gap and port details, a physical home-Wi-Fi scan; no automatic scan on launch/reconnect. |
| R03 | Show IP addresses, available MAC addresses and detailed information honestly. | Real device details, grouped IPv4/IPv6, MAC provenance/unavailable state, service/model/hostname fixtures and clipboard contents. Unknown values remain unknown. Router PTR/DHCP records do not supply identity or other fields in new scans. |
| R04 | Improve recognition using available local evidence. | Real Android DNS-SD registration/resolution, dynamic service types, fresh-response admission, cached NSD identity quarantine, current same-peer service-record matching, IPv4/IPv6 multicast, deterministic identity priorities, guarded UPnP HTTP exchange, SADP and WS-Discovery transport/parser checks. Bounded independent enrichment queues and a local Cast name-only fallback for confirmed devices with no useful name. Cached advertisements alone do not establish presence. No proprietary recognition or universal inventory claim. |
| R05 | Group related addresses without merging unrelated devices. | Self once; explicit grouping evidence; per-address details; equal-name separation; conflicting MACs; input-order independence; IP reuse does not inherit identity. |
| R06 | Show trustworthy scan status. | Actual range, checked-address count, timestamp and per-source health; failure/truncation remains Partial; coverage is distinct from device count; stopped scans remain Partial. ICMP silence is normal; execution/permission failures remain visible even when TCP succeeds. TCP refusals and a single accepted connection cannot establish presence. |
| R07 | Preserve results and status appropriately. | Fresh list after process restart, preservation only in RAM across Activity recreation, network isolation, empty results on a new/invalid scan, and late-write fencing after a reset. A new Wi-Fi session may legitimately start a new inventory. |
| R08 | Keep Back navigation at the selected list position. | Toolbar and Android Back, long lists, filtered lists, changed row heights, theme change, real-phone row bounds. |
| R09 | Search and copy useful device details. | Name/IP/MAC/service filtering, empty matches, grouped results and actual system clipboard content. |
| R10 | Provide polished light and dark layouts and Lucide device pictograms. | Native list/detail/menu/dialog renders in both themes; selectable addresses; consistent icon foreground; no R/D letter badges. |
| R11 | Keep the information hierarchy simple and readable. | Scan controls before the device section; compact main status; compact known device facts, labelled measured response time and expandable discovery evidence; readable scan-details explanations; expandable exact method diagnostics; all five Options entries. |
| R12 | Display the actual SSID prominently with optional permission. | Native SSID callback, Android denial/grant, scanning without location, quote/sentinel/bidi handling, foreground cache clearing, long SSIDs and sample separation. |
| R13 | Respect local-network permission and Wi-Fi scope. | Android 17 OS denial/approval; no launch scan before consent; no cellular scan; custom subnet/range limit; responder/URL scope and socket-binding checks, including native ICMP exact-Network binding, reply validation, descriptor cleanup and bounded cancellation. |
| R14 | Handle Wi-Fi changes and foreground lifecycle safely. | Real virtual-Wi-Fi disconnect/reconnect without reopening; no stale connection details; Stop, Android Home, return, recreation and explicit restart. No new background jobs. |
| R15 | Make all menu actions consistent and safe. | Connection details, name access, range validation/Cancel, session appearance changes, privacy explanation, no main-menu delete action, RAM-only Clear current results inside Privacy; privacy text explains RAM-only state. Legacy-storage cleanup and process-death cases run only in emulators. |
| R16 | Remain usable with larger text and different orientations. | Native 200 percent text, scrolling, landscape, bounded dialogs, visible fixed actions and reachable final menu entry. This is not a formal TalkBack certification. |
| R17 | Minimize permissions and protect local data. | Exact seven-permission allowlist; no background location/ads/analytics/storage permission; no app-written scan files, database, preferences, history or logs; legacy preferences/backups removed; no user-data Bundle restoration; backup/transfer exclusions; one exported launcher; malicious-input and session-fencing regressions. |
| R18 | Run on supported Android versions and update compatibly. | Minimum API26, modern API36/37 runtime matrices, signing/package checks, retained certificate, compatible physical-phone update preserving Android grants while removing prior app-stored data. |

## Boundaries

- The app scans devices that respond to supported, bounded local protocols. Silent devices, network isolation and self-reported or absent identity limit recognition. Complete means the planned checks finished, not every physical device was found.
- A MAC address can be unavailable on modern Android; local-network approval does not grant unrestricted access to neighboring MACs. No address or hardware identity is fabricated.
- Physical-phone testing preserves Android permission grants; this upgrade intentionally removes prior app-written data as requested. Emulator-only tests may clear data, change grants, disconnect virtual Wi-Fi and create clearly labelled fixtures.
- Automated protocol fixtures, real emulator networking, real phone networking and visual inspection are reported separately. An emulator responder is not a physical camera or printer.
- Security regression checks do not establish the absence of every possible vulnerability. Recognition parity with Fing, every Android/OEM combination and exhaustive IPv6 enumeration are not acceptance claims.

## Repeatable runtime matrix

`scripts/test-android-e2e.py` refuses physical-device serials, installs the chosen release/test APKs and records each suite's result. Run permission denial first on API37, and reset only the isolated emulator's optional-location grants for the SSID suite. `scripts/test-network-journey.py` independently operates visible controls while changing virtual Wi-Fi state. See versioned reports for exact commands, failures, fixes and reruns.

As agreed on 2026-10-23, a full app process restart requires a fresh scan. Network Lens uses process-scoped Wi-Fi session tokens rather than persisting a Wi-Fi fingerprint. Results live only in RAM for the current observed connection. The appearance override lives only in RAM for the current process. A cold start has no results and follows the system theme. Android manages permission grants independently.

As agreed on 2026-10-24, Network Lens must not write any scan data or app preferences to persistent storage. Remove the saved-data delete action; keep a RAM-only Clear current results action inside Privacy. Results, UI filters and appearance are process-local only; clear prior observations at each scan. Remove credential/device-protected netlens preference files and backups from older versions. Manual Copy remains an explicit user action through Android; OS-managed installation, permission grants and caches are outside app-written storage.

For the approved branding update, the visible name is Network Lens. Use the approved mint Wi-Fi/lens concept with its longer handle, a full teal field and no white disc. The main-screen header uses the launcher’s actual foreground artwork without its background/mask, at a larger size and with a readable theme accent; details retain Back navigation. Keep app ID/signing continuity and RAM-only behavior. Native previews must cover light/dark, large text, circle/squircle/rounded masks and Android themed icons.

## Kotlin migration acceptance (0.3.0)

All hand-written production code must be Kotlin. The build must reject remaining production Java (Android-generated R.java and the independent Java test harness are allowed). Preserve the native UI, logo assets, package/signing identity, min SDK, permissions, fresh-scan admission and RAM-only data policy. Run the existing host and Android suites against the port, documenting any test-environment corrections and confirming them against the Java baseline, verify legacy and modern NSD/ICMP paths, exact-network binding, bounded/cancelled work, navigation/theme/SSID, and cold-start cleanup. Pin and attribute the Kotlin standard library and build tools; the compiler itself must not be packaged.

## HomeKit and Matter — 0.3.1

- Browse the five HomeKit TCP/UDP, Matter operational/setup and Thread border-agent types explicitly, within the existing type/service/query budgets.
- Use current HomeKit `md`, `ci` and `sf`; unknown or malformed status stays unknown. Decode `sf` as flags. Keep pairing IDs separate from hardware MAC/vendor facts.
- Recover a HomeKit identity without a native NSD address only from a complete same-packet PTR/SRV/TXT plus exact target A/AAAA equal to the current sender, inside the existing Wi-Fi scope. Proxy-only, incomplete, expired, off-scope, cached and stopped-session data cannot supply that identity.
- Count live-record recovery distinctly; a recovered instance cannot hide another instance/source failure. Keep resource/admission bounds and late-callback rejection.
- Do not use a UDP or Matter operational advertisement as permission to attempt its port over TCP. A cached candidate is not an active endpoint; ordinary TCP-only confirmation still needs two spaced accepted connections.
- HomeKit roles use consistent Lucide pictograms in light/dark themes and accurate accessibility labels. Setup status remains under evidence; copy includes it with its advertised qualifier. Locally derived self roles use a neutral type label.
- Retain RAM-only state, fresh scans, explicit user scan start, selected Wi-Fi binding, IPv4/IPv6 layout, Back position and existing permissions. This version does not claim Thread proxy inventory, route expansion, Bluetooth discovery or accessory control.
- Record whether physical scans finish their planned window or are interrupted; a stopped scan must not be presented as a complete discovery verification.

## Legacy service types — 0.3.2

Normalize native Android service types with a single leading separator, missing trailing dot, case differences or a local suffix before matching current peer records. Do not relax the wire DNS parser or accept malformed/foreign types. Cached native TXT remains unusable as identity, two accepted TCP connections remain required, and UDP/Matter advertisements never imply TCP. Verify real Android 8 NSD registration/resolution and a controlled TCP fallback under actual platform ICMP denial; an emulator router without an open service is not a valid TCP fixture. Remove the legacy packaging waiver once the current full matrices pass.

## Fresh split-service resolution (0.3.3)

- General DNS-SD identity may recover without native callbacks only from a complete PTR/SRV/TXT/address association received during this scan from one sender, whose address matches the SRV host address.
- Separate packets and out-of-order arrivals work; literal dots remain part of an instance label. Cross-peer and proxy associations never qualify.
- Wire TTL, a ten-second maximum assembly age, goodbye and cache-flush semantics prevent reuse of expired/withdrawn records. Multi-address members within one reply burst survive cache flush; older unrefreshed members expire after one second. Timer-driven completion must publish newly unambiguous details once, without requiring another datagram or indefinitely extending the quiet window. The assembler is process RAM, is new per scan and closes on Stop.
- Missing records trigger bounded, paced, local-only SRV/TXT/A/AAAA questions. Observe actual questions and replies through selected-interface Android multicast sockets.
- Query/record/peer/byte caps report limits; no automatic route expansion or IPv6 sweep.
- SNMP/Ubiquiti and IPv6 echo diagnostics do not ship as new production discovery engines.

## Settings navigation (0.3.4)

- Overflow contains exactly Wi-Fi details and Settings. Theme remains accessible through the labelled header icon.
- Settings has Scanning, Permissions and Privacy & about sections; all actions remain reachable at large text sizes.
- System-grant status is read accurately for the current Android version. Wi-Fi-name permission is optional; granting it from Settings refreshes status without starting a scan.
- Open Android settings opens this app’s actual system details. Return refreshes permission status and retains the in-app Settings screen.
- Toolbar and Android Back restore the originating filtered list position or device detail view. Same-process Activity recreation preserves this navigation without saved state or disk writes.
- Session range validation, Cancel, automatic range and confirmed RAM-only clearing remain available. Clearing preserves range and appearance.

## Freshness and connection recovery (0.3.5)

- Connection identity includes an in-process random token, Android network handle and IPv4 attachment/interface; DNS changes, unrelated IPv6 routes and same-prefix temporary-address rotation preserve the active scan and unaffected list.
- Immutable prefix/own-address snapshots update without cross-thread map mutation. Added direct-link prefixes are admitted; withdrawal refuses obsolete work, fences its callbacks, prunes affected IPv6/retired self addresses and retains unaffected observations with honest Partial status. No routed prefix is newly admitted.
- Network replacement, loss/reconnect and IPv4/interface changes clear results and old range, filter and status on both foreground callbacks and resume. A cold process remains empty.
- Native callbacks never establish identity from cached TXT or a parallel non-expiring cache. Only complete, current, same-peer PTR/SRV/TXT/host associations publish service identity. Goodbye, expiry and cache-flush replacement cannot be bypassed by a late NSD callback.
- Both legacy preference storage domains are attempted even after a false return or exception. A cleanup failure does not crash or falsely claim no old data; show an accessible retry/recovery page and block discovery until cleanup succeeds. Retry success returns to an empty ordinary app without starting a scan.
- Existing RAM-only behavior, seven permissions, local binding, bounded/cancellable I/O, search/Back, both themes and exact APK/signature checks continue to pass.

- Android NSD handle initialization cannot bypass the scan deadline. A stalled OS constructor is shared by at most one daemon worker per process, callers have a bounded/cancellable wait, and native-source failure leaves direct multicast discovery operational. Missing/failed handle acquisition can recover on a later scan.

## Routed IPv6 accessories (0.3.6)

- Retain direct-link scope separately. Admit routed HomeKit/Matter candidates only through an explicit IPv6 /64 or more-specific route on the selected Wi-Fi with a link-local next hop. Reject default/broad/wrong-interface/non-unicast/more-specific reject routes.
- Obtain fresh correlated ICMPv6 echo replies with random nonce, socket identifier and sent sequence; never show an unconfirmed advertisement. The connected kernel ping socket supplies and validates IPv6 checksums.
- Two bounded attempts allow 4.2 seconds for sleeping devices; 32 candidates and four independent workers share the existing overall deadline. Stop, Home and route withdrawal fence late work.
- Names/models from a Wi-Fi proxy require the same sender's current PTR/SRV/TXT/AAAA graph, and a separately confirmed endpoint. Expiry, goodbye, replacement and cross-sender graphs cannot newly establish identity. Native cached TXT is never promoted.
- Never probe TCP, HTTP or NetBIOS on routed candidates. Direct IPv6 TCP services retain their existing confirmation fallback.
- Scan details distinguish excluded, silent and failed checks. Device details identify ICMPv6 timing and routed/proxy provenance without asserting that routing proves Thread.
- Native Android socket echo and controlled property/transport fixtures are separate from a real Thread accessory test; report the latter as unverified if hardware/routes are unavailable.

## Failure diagnostics (0.3.7)

- Terminal reports retain two native timeout reasons and all normal six-counter diagnostics exceeding the old 200-character limit; each source value remains bounded to 512 characters.
- Scan details name failed/incomplete methods near the top, remain Partial, preserve collapsed technical details, and work in both themes and large text.
- A later complete scan removes the previous failure summary; no previous scan or history is retained.
- Real phone repeats must capture every failed/incomplete source and reason before continuing, or stop with the current scan intact.
