# In-app updates

Available starting with Network Lens 0.3.17. If you have that version or newer,
use Check for updates to obtain 0.3.25. Older installations need the current
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

## Presentation improvements in 0.3.23

The update screen prepares update content before showing the dialog and retains
the same text and action views during download progress. An unrelated refresh of
the page behind it no longer dismisses and recreates the update window. Appearance
or configuration changes still rebuild it with the correct theme and text size.
Actions change when the update step or installer-confirmation availability
changes. Transfer, verification, cancellation and installation rules are unchanged.

All in-app dialogs now use a distinct surface and a stronger dimmed backdrop.
These presentation improvements are included in 0.3.23. Release notes are not
yet shown inside the app; the GitHub release page lists what changed.

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
The download digest checks that the received bytes match GitHub's asset; the
pinned signing certificate establishes the expected publisher identity.
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
The 0.3.20 regression checks also exercise installer-session opening failures on
the main thread. Storage, permission and installer errors leave a recoverable
message, abandon the session and cancel its pending callback.

See [current release verification](VERIFICATION-0.3.25.md) for artifact-specific
checks and [BUILDING.md](BUILDING.md) for commands. Earlier live in-app update
journeys are recorded with the corresponding release notes and assets, including
[the original updater release](VERIFICATION-0.3.17.md). A signed in-place emulator
upgrade and a live GitHub download through the app are separate checks; the release
notes identify which was performed. These results do not establish compatibility
with every Android version or device-management policy.

Protocol references: [GitHub releases API](https://docs.github.com/en/rest/releases/releases),
[Android PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller),
[Android install permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()).
