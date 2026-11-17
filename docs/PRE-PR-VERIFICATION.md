# Unpublished candidate verification — 2026-11-17

Historical implementation/candidate evidence for functionality included in
**0.3.19**. Candidate version labels, hashes and publication status below describe
those earlier checks. See [final release verification](VERIFICATION-0.3.19.md).

This record covers the broader discovery and home-user interface changes on
`codex/omada-discovery`. The candidate still identifies itself as **0.3.18
(version code 46)**. It is not the published 0.3.18 APK and has not been uploaded
as a release. A release needs its own version and final artifact verification.

## Artifact identity

| Artifact | SHA-256 |
| --- | --- |
| Development APK | `b53322e86b22e5148a6b895f8eb921d24771bc28af87ab04d6d47e3136f1ca6a` |
| Public-key-signed local candidate | `c0c340c7d369e429c7e89ac12a1f69e1f317a280c484eb784eb409458e1da1b3` |
| Instrumentation APK | `92338b5f9acd9f15c16b6cad9c7cad2e53b459b3a74eaa20d00c11361f425565` |

All 48 non-signature application payload entries match between the development
and public-key-signed builds. Emulator runners verify the installed application
and instrumentation bytes. Test artifacts and captures remain ignored local files.

## Completed checks

- All **39 host suites** passed, including the **53 Matter recognition checks**.
- Development, instrumentation and public-key-signed direct builds passed.
  Packaged permission/security audits and offline dataset checks passed.
- The isolated release-signing integration suite passed **116 assertions**.
- The emulator Wi-Fi disconnect/reconnect journey passed **18 checks**, including
  no automatic scan, no cellular fallback, cancellation and optional-location denial.
- Synthetic screens were inspected in light/dark appearance and at enlarged text.
  Actual emulator theme changes also verified light/dark system-bar contrast.
- Working-tree whitespace and the repository prohibited-file/credential check passed.
- A fresh independent Deep review approved the focused test corrections below.

## Android matrix results and retries

These are **not clean, uninterrupted all-green matrix runs**. Preserve the original
failures when assessing readiness; passing retries do not erase them.

| Platform | Full run | Focused follow-up |
| --- | --- | --- |
| Android 8 / API 26 | 36 of 37 modes passed, 3,282 checks; `discovery-upgrade` timed out | The same mode passed all 16 checks after restarting the emulator and restoring Wi-Fi readiness |
| Android 17 / API 37 | 38 of 39 modes passed, 3,367 checks; `permission` could not find the native deny button | The unchanged permission test passed twice in succession (17 checks each), and after `performance` (12 + 17 checks) |

Android 16 / API 36 passed all **15 selected modes, 2,619 checks** in one completed
run: device-identities, home-user, app-updates, audio-zones, recog, local-metadata,
typography, failure-reporting, scan-details, service-retries, discovery-upgrade,
confirmed-fixes, full, session-only and session-cold. An earlier run was deliberately
interrupted to reduce host resource contention; it is not counted as completed.

Android 8 diagnostics showed repeated timeouts in the platform's native mDNS
daemon. The test obtains Android NSD directly; production initialization uses a
bounded service handle and reports failure while allowing direct queries to
continue. The first post-reboot attempt ran before Wi-Fi was ready and failed its
network prerequisite. The later successful run does not prove that every Android
8 vendor's discovery service is reliable.

The intermittent Android 17 permission-dialog failure is not conclusively
explained. The app requested the system permission activity, and a separate
manual emulator interaction displayed the expected native controls. No permission
implementation or assertion was weakened to obtain the successful retries.
This remains a verification caveat before publication.

The earlier Android 17 clipboard failure recorded in
[discovery coverage](DISCOVERY-COVERAGE.md#matter-grouping-follow-up-2026-11-17)
did not recur after a fresh emulator boot: `device-identities` passed **695 checks**
in both the initial and final broad runs, without clipboard code/test changes.

## Test corrections made during verification

- The full smoke test now checks the existing private update-result receiver and
  exact eight-permission manifest, replacing assertions from before in-app updates.
- Raw hostname checks first open **Show technical details**, matching the current
  deliberately collapsed interface.
- The queued scan-result test checks that its fixture belongs to the selected
  Wi-Fi scope, then waits up to five seconds for the asynchronous final update,
  rather than assuming completion after 800 ms. It retains the exact one-device
  and stopped-scanning assertions and reads the UI state on the main thread.

No production code was changed during this verification pass.

## Remaining boundaries

- The authorized Pixel 8 was updated in place with the public-key-signed local
  candidate and its installed hash verified. A fresh foreground Wi-Fi scan
  finished with 254 / 254 IPv4 addresses checked. Four methods reported failures:
  Apple model lookup, Cast group information, Spotify information and VSSL zone
  status. A finished scan is not proof of complete discovery or identification.
  No household results or captures are included in this repository.
- The physical list correctly presented an Ecobee Premium as a thermostat. A
  verified Samsung search returned no matching devices; the TV's presence and
  power state were not established, so model recognition remains unverified.
  Subsequent Apple/TP-Link searches were not completed because remote text entry
  did not update the search field. Those attempts are not recognition failures.
- The physical scan's technical report showed one Apple model reply from two
  requests, with one response-read failure. Cast device metadata produced 15
  replies and two responses without extra details, but all 17 Cast group requests
  reported no valid metadata. These observations need compatibility follow-up;
  this verification does not establish their cause or prove a new regression.
- Hardware/firmware compatibility beyond the observed devices, a TalkBack journey,
  and a real newer-version GitHub download/install flow are not verified here.
- Large synthetic lists are measured, not certified to a latency target; the
  1,024-endpoint rich-metadata cases still take seconds on these emulators.
- No PR, merge or publication was performed during this pass.
