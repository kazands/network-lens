# Home-user interface wording

Home users and enthusiasts are the primary audience; network technicians and IT
users retain access to the underlying observations. Preserve the established
Network Lens branding, appearances, icons and native Android navigation.

| State | User-facing wording | Meaning |
|---|---|---|
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

These interface changes remain included in 0.3.20. Physical
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
[current release verification](VERIFICATION-0.3.20.md) for the final artifact and checks.
