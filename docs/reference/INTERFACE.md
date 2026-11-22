# Home-user interface wording

## Device layout and search — 0.3.38

The current list gives names the full trailing text area, with useful room names
and a quiet address count beneath. Category and service badges are kept in
details. The compact scan card preserves complete status text and 48dp actions.

Details start with the identity card's labelled IPv4 and available endpoint MAC,
then the existing Services & ports section. Identification explanations, grouped
addresses, response timings and source-labelled observations remain in collapsed
technical details. These layouts supersede the quick-facts and standalone
address/explanation sections described in the historical migration below.

Search retains its active editor and list state while text changes. The keyboard
Search action hides the keyboard and releases field focus without clearing the
query. Back from a result preserves the query and list position.

## Design refinement — 0.3.36

The [design system](DESIGN-SYSTEM.md) centralises complete light/dark Material
schemes, typography and shape tokens. Device icon tiles use muted colours from
the existing category filters. Menus, dialogs and the scan sheet use the elevated
surface role; the dimmed backdrop and privacy boundaries remain.

The scan card separates the network name from its scope, adapts at narrow widths
and enlarged text, and uses one decorative radar instead of a spinner plus bar.
Its determinate rim describes only the address pass. The live textual status
continues to describe identification and actual method outcomes. Animation
settings are observed while open. Idle, paused, off-screen and reduced-motion
states do not keep an infinite animation. Only a newly observed successful scan
with results receives a closing ring.

List counts use "devices" for grouped results; Scan details retains separate
device-group and address-endpoint counts. Counts update immediately. Technical
disclosures, theme changes and navigation retain their existing instant behaviour.

## Compose interface — 0.3.34

Version 0.3.34 uses Jetpack Compose throughout: screens, menus, dialogs,
device rows and scan details. Earlier View-based behavior is retained only in
the historical sections below.

The scan card brings the network, action, progress and status together. Results
use a lazy list with stable endpoint keys, larger Lucide icon plates and quick
filters for unnamed devices, network equipment, smart home, computers and media.
Search still includes hidden MAC addresses and reported fields. Dual-stack rows
show their preferred IPv4 address and an additional-address count; IPv6-only
rows retain their full IPv6 address. Details show every grouped address, IPv4
before IPv6, with explicit copy controls.

Device details use an identity card, an adaptive quick-facts grid and services
separate from observed ports. They never pair a service with a port without
evidence. Raw fields, independent endpoint observations and identity explanations
remain available through disclosures and complete device copy. Larger text uses
a single-column facts layout; wide screens can show list and details together.
An open HTTP port alone does not produce an Open Web UI action.

Scan details is a bottom sheet. Other dialogs retain a 65% dimmed backdrop,
scrollable bodies and fixed actions. The updater has one stable Compose dialog
whose content changes with update state. The report preview keeps page controls
outside the scrolling text. The manual Envisalink action and dedicated TPI
reader are removed; there is no replacement probe of its single-client service.

`UiMemory` keeps filter, disclosure and scroll choices in RAM. Generation checks
prevent a retired scroll collector from restoring an old position after a new
scan, page or search. List restoration keeps an item key and offset, then resolves
that key in the current results when returning from details or Settings. If the
item has disappeared, it uses a bounded position in the remaining list.
`MainActivity` still owns scan lifecycle, network fences,
updates and exports. Compose receives snapshots and sends `UiAction` events;
recomposition does not start discovery or update requests. The Activity clears
saved-state bundles and restores nothing from disk. Search and range input keep
the private keyboard flag; explicit copies keep sensitive clipboard marking.

Before creating Compose content, `UiPrivacy` disables Compose's virtual-content
capture feature and excludes the window's view hierarchy from platform content
capture and autofill. Dialog, popup and bottom-sheet roots apply the same policy.
This protection does not use `FLAG_SECURE`: user screenshots and accessibility
semantics remain available. The pinned runtime's experimental capture switch is
covered by `compose-privacy` regressions and must be rechecked on dependency updates.

See [Compose build and test instructions](../BUILDING.md#compose-dependencies).
Synthetic emulator checks do not establish physical scrolling performance,
TalkBack behavior or recognition accuracy.

Home users and enthusiasts are the primary audience; network technicians and IT
users retain access to the underlying observations. Preserve the established
Network Lens branding, appearances, icons and native Android navigation.

| State | User-facing wording | Meaning |
|---|---|---|
| First address pass running | Checking addresses · N% | Percentage of the planned IPv4 addresses checked in the first pass, not percentage of the entire scan or devices found. |
| Address pass ended or omitted | Identifying devices… | Animated indicator while discovery, retries and device lookups continue; no invented percentage. |
| No usable reported name/model or supported product/family title | Unnamed device | Identity remains unknown; the address distinguishes the result. |
| Scan attempt has ended | Scan finished | Describes the scan lifecycle, not guaranteed discovery or success of every method. Failures, coverage and limits remain in Scan details. |
| Scan was cancelled | Scan stopped | Shows the responses collected before stopping and the actual coverage. |
| Raw device fields and discovery observations | Show technical details / Hide technical details | Expandable per address; included in copied details regardless of expansion. |
| Supported product interpretation | How this was identified | Describes the actual evidence: model code, protocol report or Matter IDs. |

The list does not use “Partial scan” or “Some results may be missing” as a status.
Scan notes explain specific failed or unfinished checks. The internal completeness
flag and method outcomes remain unchanged; a finished attempt can still have
unsuccessful checks. No discovery improvement is claimed by this wording change.

Retained scan diagnostics use a shared 640-method bound: all 512 admitted mDNS
types plus room for the other discovery and lookup methods. The JSON reader uses
the same bound. Unexpected overflow is counted and disclosed in Scan details;
it cannot silently suppress the aggregate failure explanation. The count survives
report round trips and Wi-Fi scope changes. Reports remain in memory only.

Unknown-device guidance explains that the device replied without a usable name
or model. Recovery guidance suggests checking that the device sought is awake
and on the same Wi-Fi, then scanning again. It also explains that some devices
do not share identifying details. It never promises that another scan will
identify the device or resolve failed checks.

The primary hardware category stays separate from service capabilities. Raw
service-reported types are in technical details, so an AirPlay report does not
compete with a known thermostat identity. Missing manufacturer/model/MAC values
are never filled by explanatory copy. Matter registry explanations refer to
vendor/product IDs rather than claiming a reported model exists.

Main buttons, toolbar actions and device-row actions share native Button
accessibility semantics while retaining their existing appearance. Dialog actions
use the same helper. Labels continue to describe the current action.

## Layout refinement in 0.3.23

The Options menu remains available on Settings, with only Wi-Fi details and
Check for updates there; it omits the redundant Settings entry. All in-app
dialogs use the elevated card surface and a 65% dimmed backdrop. This includes
scan details, Wi-Fi information, Wi-Fi-name explanations, custom range, privacy,
clear-results confirmation and updates. Dialog dimensions are reapplied only
when they change. The update dialog preserves its views during progress and its
window during unrelated page refreshes; see [updater details](APP-UPDATES.md).
The [surface review](../archive/UI-SURFACE-REVIEW.md) records the broader screen audit,
regressions and remaining physical-device verification.

List, detail and settings content use 16dp horizontal margins. The list has
less space above the results and between each icon and its labels; text sizes
and address wrapping are preserved. Settings actions are left-aligned rows
with at least 48dp touch targets instead of nested outlined buttons.

Grouped details show one identification explanation only when every endpoint
independently resolves to the same product and explanation, and the group has
no product conflict. Otherwise explanations stay with their individual
addresses. The missing-MAC explanation appears once, while each unknown MAC
value, address, source, technical disclosure and copied observation remains
separate. This changes presentation only, not recognition or grouping.

Scan progress measures the first address pass only. The indicator becomes
animated when that pass ends, including while retries or lookups remain. Scans
without an IPv4 sweep use the animated indicator throughout. Final status and
scan diagnostics retain their existing completion, failure and Stop semantics.

### Refinement verification — 2026-11-18

The direct application and instrumentation builds passed. API 37 passed 2,088
checks across `options`, `navigation`, `device-identities`, `typography`,
`scan-details`, `failure-reporting` and `grouping`. The first runner ended with
signal 15 before recording the grouping result; that mode passed separately.
Both runs verified the installed application and test APK hashes.
API 26 also passed `options` (113), `navigation` (38) and `typography` (829),
with the same installed hashes: 3,068 passed checks across both platforms.
The full host suite was not rerun for these Android presentation changes.

The earlier settings reachability failure did not reproduce with the compact
layout. The existing strict visibility check passed after returning from Android
settings, changing appearance and scrolling from the top, including large-text
and landscape paths. Failure messages now include control and viewport bounds;
no scroll assertion was weakened or delay increased. This does not establish
the cause of the earlier intermittent failure.

Synthetic layouts were inspected in both appearances, at normal and 200% text,
with 320dp and 411dp checks. Fresh native light/dark captures verified system-bar
contrast; fixture screenshots with a clipboard overlay were not used as clean
previews. Physical TalkBack and phone testing remain unverified. No release,
version bump or phone installation was performed for this refinement.

These changes do not alter device discovery, scan success criteria, saved-state
policy or APK release version. The separate result-list recycling proposal is
not implemented in this interface pass. Physical TalkBack and hardware
performance need device testing in addition to the emulator regression checks.

## Verification — 2026-11-17

The direct development APK build and instrumentation build passed. The 39 host
suites passed for this interface work. Final installed APK and test-binary hashes
were verified by the emulator runner; all selected native checks passed:

| Platform | Modes | Checks |
|---|---:|---:|
| Android 8 (API 26) | 7 | 1,183 |
| Android 17 (API 37) | 12 | 2,196 |

Both platforms ran `home-user`, `details-upgrade`, `typography`, `scan-details`,
`failure-reporting`, `grouping` and `options`. API 37 additionally ran
`device-identities`, `recog`, `navigation`, `mac-vendors` and `ws-discovery`.
The status checks cover successful and failed methods, cancellation, exact
coverage, report round trips, running/first-use/sample states, and retained
method failures despite the neutral finished label.

Two initial test failures expected raw fields before expanding the new technical
disclosure. Those flows now expand the disclosure and retain their model,
manufacturer, endpoint-specific outcome and typography assertions; both pass.
Synthetic renders were inspected in light/dark mode and at 200% text size.
Repository diff and working-tree hygiene checks passed. Test evidence remains
local and ignored. No household scan or physical phone was accessed.

These interface changes remain included in 0.3.23. Physical
TalkBack, hardware performance and real-device recognition changes are not
validated by these interface checks. No release or phone installation was performed.

## Review corrections verified — 2026-11-17

The diagnostic-retention and queued-EAP-status findings were fixed. Direct
application and instrumentation builds passed after the corrections. Both API 26
and API 37 passed 859 targeted checks: `failure-reporting` (53),
`device-identities` (673), `scan-details` (70), and `freshness-fixes` (63).
Both runners verified identical installed application and test APK hashes.

Regression coverage includes all 512 admitted service types plus other methods;
early and late failures beyond the old 64-entry limit; bounded overflow counts,
JSON round trips and aggregate-error disclosure; Wi-Fi scope replacement; and
EAP work queued behind two occupied workers at Stop and deadline cleanup.
Completed lookup outcomes remain unchanged.

The API 37 emulator initially stalled during startup and was restarted before
testing. No application/test failure occurred in either completed matrix.
Whitespace and working-tree hygiene checks passed. Physical-device tests and the
full host suite were not rerun for these Android reporting/cleanup corrections.
No version change, publication or phone installation was performed.

The candidate verification history above predates release versioning. See
[current release verification](../VERIFICATION-0.3.38.md) for the final artifact and checks.

## Scrolling stability

Wi-Fi capability notifications that leave the connection, addresses, routes and displayed connection information unchanged do not rebuild the current page. Connection reconciliation still runs, and network replacement or withdrawn scope still clears or prunes results. Device-details scroll restoration runs after layout and before drawing. The `detail-stability` emulator mode checks repeated notifications, expanded evidence, necessary redraws and network replacement in both appearances at normal and enlarged text sizes.

### Troubleshooting report

Scan details offers a detailed report alongside the counts-only summary. A privacy choice precedes a static, paged preview. Share sends the full report as one temporary text attachment; Copy this page includes its page number. Preview/sharing is unavailable for sample results, active scans and retired scan sessions. See [coverage and lifecycle](TROUBLESHOOTING-REPORT.md).
