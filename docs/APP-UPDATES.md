# In-app updates

Available starting with Network Lens 0.3.17. If you have that version or newer,
use Check for updates to obtain 0.3.18. Older installations need the current
signed APK installed once using the download link.

## Using the updater

1. Open **Options → Check for updates**, or **Settings → App updates**.
2. The app checks the public Network Lens GitHub releases, including numbered
   development previews. It shows your installed version and any newer version.
3. Tap **Download update**. Keep Network Lens open during the download.
4. Tap **Install update**. If Android asks, allow installs from Network Lens,
   return to the app and tap **Install update** again.
5. Approve Android's installation prompt. Reopen Network Lens after installation
   and start a fresh scan.

There is no GitHub login, browser download step, automatic checking, notification,
background download or silent installation. Android controls approval and can
block installation under device-management policy. The check uses the phone's
normal internet connection, which may use mobile data. Recognition still uses
bundled offline catalogues and local discovery.

## Verification and privacy

The app requests only the fixed public releases feed, then the exact APK asset
for the highest supported numeric `vX.Y.Z` release among the latest 100 entries.
Drafts are ignored. A missing or incomplete latest release produces an error,
not an “up to date” result. Non-numeric release tags are not supported.

HTTPS downloads are restricted to this repository's release URLs and GitHub's
asset hosts. Downloads have size, redirect and time limits. The app checks the
GitHub-provided SHA-256 digest and declared size, application ID, version name,
increasing Android version code, minimum Android version and the pinned public
signing certificate. Android's installer performs final signature verification.
A locally development-signed build cannot update to the public signing key; the
app reports the mismatch and never uninstalls the existing app.

No scan results, device addresses, Wi-Fi names or account credentials are sent.
GitHub receives the connection's public IP address and a fixed app user-agent as
part of the request. APK bytes are temporarily held in private cache and Android
installer staging, not Downloads/shared storage. Interrupted downloads are
removed. A verified download can remain while the update dialog is closed or the
user visits Android's install-permission settings, until the activity closes.
Completed or cancelled installer sessions are cleaned up; abandoned temporary
files/sessions are removed when the app next starts after process death. No scan
history or update preferences are saved.

The manifest adds `REQUEST_INSTALL_PACKAGES` and one non-exported result receiver.
Only the launcher is exported. There is no file provider, background service or
new storage permission. A future Google Play distribution needs a separate review
of its update mechanism and permission policy.

## Verification

Host regressions cover feed/version parsing, missing or ambiguous assets, URL
boundaries, size/digest/signing admission, interrupted and cancelled transfers,
rate limiting, deadlines and cleanup. Emulator mode `app-updates` covers themed
and scaled dialogs, foreground cancellation, queued dialog replacement, private
callback/session checks, invalid APK rejection and a real PackageInstaller
pending-user-approval callback. It does not approve or complete an installation.
Verification for this source change: all 32 host suites passed, with the final
updater-specific suite passing 67 checks. The direct APK build and packaged
permission/security audits passed. Native `app-updates` passed 128 checks each on
API 26 and API 37; `options` passed 115 checks each. API 37 navigation, session-only,
cold-session and scan-efficiency checks added 95 passes. The actual public GitHub
feed was accepted as version 0.3.16 with its preview flag, size and digest. A live
API 37 emulator check through Options contacted GitHub and displayed the installed
0.3.16 build as up to date.
See [BUILDING.md](BUILDING.md) for the direct test commands.

Versioned release and in-place upgrade checks are recorded in
[0.3.17 verification](VERIFICATION-0.3.17.md). The post-publication live in-app
installation result is recorded with the GitHub release assets/notes, because that
check requires the new public release to be available. An older-version private
test build includes the updater for this check; the original 0.3.16 release does
not have it. These checks do not establish compatibility with every Android/OEM
version.

Protocol references: [GitHub releases API](https://docs.github.com/en/rest/releases/releases),
[Android PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller),
[Android install permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).
