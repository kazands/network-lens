# Interface surface review — 2026-11-18

This review covers the interface changes released in 0.3.23. Candidate checks
below used the pre-versioning development build; see
[0.3.23 verification](VERIFICATION-0.3.23.md) for the exact release artifacts. Evidence uses isolated emulators and synthetic devices;
no household results or phone screenshots are stored in the repository.

## Confirmed issues and changes

1. Settings explicitly removed the global Options button. It now retains Wi-Fi
   details and Check for updates; the menu omits Settings while already there.
2. Dialogs used the page background and a 42% dim layer, leaving the underlying
   text visually prominent, especially in dark mode. The shared dialog now uses
   the card surface and a 65% dim layer. Body and secondary text contrast ratios
   are 14.60:1 / 5.11:1 in light mode and 13.53:1 / 6.91:1 in dark mode.
3. Every update progress notification removed and rebuilt the dialog's text and
   actions and requested another layout. `AppUpdateDialog` keeps those views,
   changes text only when necessary, and changes actions only when the step or
   installer-confirmation availability changes. Content is prepared before show.
4. An unrelated page render recreated the update dialog. It now keeps the same
   window unless its palette or configuration changes. Replacement still detaches
   the obsolete dismiss listener so it cannot cancel an active operation.

The update findings explain avoidable rendering churn and focus/scroll loss.
They do not establish that every artifact reported on a physical phone has the
same cause. Download validation, cancellation, signing checks and Android's
installation approval are unchanged.

## Surface coverage

| Surface | Review focus |
|---|---|
| Device list, sample, empty and scanning states | Width, progress, text scaling, search, row spacing and Back position |
| Single and grouped device details | Independent evidence, shared explanations, address wrapping, unknowns and copy |
| Settings and Options menu | Persistent global actions, no redundant Settings entry, scrolling, appearance and Back |
| Wi-Fi details and Wi-Fi-name explanations | Dialog separation, source text, optional permission actions and dismissal |
| Custom range and invalid range | Input, error visibility, keyboard space and fixed actions |
| Privacy and clear-results confirmation | Modal separation, readable explanations, cancellation and explicit confirmation |
| Scan details, expanded methods and failed/stopped checks | Modal separation, scrolling, live coverage and preserved failure reporting |
| All nine update states | Stable views, state-specific actions, scroll/focus, text scaling and appearance |
| Installer continuation and errors | Action changes without a step change, cancellation and foreground handoff |
| Cleanup recovery | Light/dark, large text, landscape and reachable recovery actions |

Android-owned permission and installer screens retain Android's appearance.
Physical TalkBack and device-specific graphics behaviour require phone testing.

## Verification

The direct application and instrumentation builds passed. The application build
also passed its packaged-permission/security audit. API 37 passed 2,706 checks
across ten modes: `app-updates` (502), `options` (128), `scan-details` (74),
`home-user` (61), `details-upgrade` (36), `navigation` (38), `typography` (829),
`device-identities` (950), `freshness-fixes` (63) and `wifi-name` (25).

API 26 passed another 740 checks: `app-updates` (502), `options` (126),
`scan-details` (74) and `navigation` (38). The combined total is 3,446 checks,
excluding the repeated API 37 update run.

The update checks cover all nine states in both appearances at normal and 200%
text, stable views/scroll/focus during progress, unrelated page renders, changed
installer-confirmation availability, cancellation and obsolete-dialog dismissal.
An initial screenshot captured a preceding synthetic state despite the current
view-tree assertions passing. The capture fixture now waits for its frame before
taking a screenshot; the update suite passed again. The repeated 502 checks are
not added to the API 37 total above.

Native captures were reviewed for all nine update states and representative
list, details, settings, scan information, range errors and recovery layouts.
The typography and device-identity suites additionally cover 320dp/411dp widths.
No household device was accessed. No GitHub update was downloaded or installed;
the installer test observes Android's approval callback and abandons its session.
The full host suite was not rerun for these Android presentation changes.

The tested development APK is still labelled 0.3.22 and is unpublished. Its
SHA-256 is `d6dd3bab761d445e0565e24d1ab714b733f8b4c1cab7d5aea22124a3b9f11af2`;
each emulator runner verifies the installed application and test APK bytes.
Whitespace and repository-hygiene checks passed. These fixes do not establish
physical graphics/TalkBack compatibility or improve discovery completeness.
