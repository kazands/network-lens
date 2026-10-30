# Network Lens 0.3.8 verification

Updated 2026-10-30 for a development preview. This is a sanitized summary of
completed checks, with remaining limits stated below. It is not a production
readiness or security certification.

## Artifact

- Android application ID: `dev.netlens`; version code: `36`.
- Minimum Android API: 26 (Android 8); target API: 37.
- Production-signed APK: `Network-Lens-0.3.8.apk`.
- APK SHA-256: `758f9a761b4cee42acccb0f10224c835da2582d19d6f0f4167f100b73451a5db`.
- App-signing certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

All 33 application ZIP entries match the accepted development-signed 0.3.8
APK. Its three old v1/JAR signature metadata entries were removed when signing
the release artifact; the new signing blocks use the production identity.
APK v2/v3 signature verification, ZIP alignment, permission allowlist and
packaged security checks passed. No new dependency or permission was added.

## Changes and controlled checks

The scanner avoids duplicate presence checks after a validated reply in the
same scan and batches presentation snapshots through its existing update queue.
Every new scan still starts fresh. Cached advertisements still require current
confirmation. Independent detail lookups, Wi-Fi scope, retries, deadlines and
the final frozen result are preserved. Results and appearance remain in memory.

Controlled fixtures retained identical final metadata hashes for 40 and 250
synthetic endpoints. Repeated already-confirmed checks opened zero additional
echo sockets instead of 20; an unconfirmed candidate still required a fresh
response. A forced burst of 200 pending updates required one snapshot instead
of 200. These fixture results do not predict every real network's scan time.

## Completed validation

- 19 application host suites: 13,735 assertions passed.
- Kotlin signing integration harness: 116 assertions passed using isolated
  generated credentials, including negative and recovery cases.
- Scanner/UI integration: 95 modes and 2,993 checks passed across API 26, 36
  and 37 runs, using the development-signed APK with identical application payload.
- API 36: 31 modes / 1,011 checks; API 37: 33 modes / 1,031 checks passed
  in their full matrix runs.
- API 26: 31 modes / 951 checks passed across runs. The initial matrix had a
  synthetic NSD registration timeout before app discovery. An isolated repeat
  also stalled, with Android daemon timeout logs. The unchanged affected mode
  passed twice after an emulator restart. The complete API 26 matrix did not
  pass in one uninterrupted run; this is not claimed as a platform repair.
- Actual production-signed APK: 20 installation/update checks on API 26 and
  20 on API 37 passed. These covered cross-key rejection, fresh installation,
  same-key updates, preserved granted permissions, exact installed bytes and
  foreground launch without an automatic scan.
- Independent Deep Kaz reviews approved the performance implementation and
  final signing implementation. The signing review's private-key recovery
  proof finding was corrected and independently confirmed.

## Physical-device evidence and open limits

Two measured scans per version were run on an authorized physical phone.
Mean elapsed time was 34.36 seconds before and 33.33 seconds after, approximately
3% lower. The small sequential sample, measurement overhead and changing network
conditions prevent a general performance guarantee. Exact per-device equivalence
was not established from those aggregate observations.

The updated development APK produced one Partial scan with a native HomeKit
address-resolution timeout and one Complete scan. The known intermittent
HomeKit (`_hap._tcp`) / HTTP (`_http._tcp`) resolution limitation remains open.
Reporting changes do not establish that the underlying discovery issue is fixed.

The post-measurement physical search/Back/theme checks were not run in the
performance session because the app left the foreground. Separate production-APK
phone checks subsequently completed on an authorized Pixel 8 running API 37:

- The explicitly authorized test-to-production reinstall passed 17 migration
  checks. Installed bytes matched the APK above; only previously granted runtime
  permissions were restored. The accepted development APK and signing key remain.
- The phone journey recorded 86 unique UI/scan conditions covering menus, Wi-Fi
  name display, range validation/Cancel, privacy, RAM clearing in dark mode,
  search, device details/evidence, both Back buttons, Settings origin/position,
  IPv6 below IPv4, both appearances and Stop.
- Three uninterrupted fresh scans were Complete, with no failed discovery
  methods. One deliberate Stop was Partial, with interrupted methods retained.
  The final fresh scan replaced the stopped status; the original appearance and
  unfiltered list were restored.
- Two earlier coordinate-based navigation attempts were inconclusive: one
  expected control was missing and a repeat failed a return-position assertion.
  Later tests verified the intended endpoint and settled row geometry. Both fully
  visible and partly clipped rows passed toolbar and Android Back checks. No app
  defect or exact cause for the earlier results was established; no app code was
  changed to obtain the passes. Original attempt reports remain local.
- A foreground interruption stopped input. The user reopened the app and the
  remaining checks resumed. No diagnostic companion, household inventory, raw
  phone view, personal screenshot or clipboard copy was saved for publication.

Additional physical release checks completed on the same production APK:

- Home during an active scan cancelled discovery and preserved an explicitly
  stopped Partial result. Every interrupted method was captured before continuing.
  Returning to the app did not restart scanning.
- A full process restart restored no device rows, followed the system appearance
  instead of a temporary app override, and did not start a scan automatically.
- A controlled Wi-Fi-off/on test verified that Android created a different
  connection. The app cleared the previous device list and waited for a new scan.
  The first manual reconnect attempt had retained rows without a recorded
  before/after connection transition. Its exact cause remains unestablished;
  that attempt is retained locally alongside the passing controlled test.
- Denying the actual Android local-network prompt blocked discovery, kept the
  list empty and displayed recovery guidance. Restoring the prior permission
  grants permitted fresh discovery without an automatic scan on return.
- The finalization session recorded 37 unique conditions. Both recovery scans
  finished their planned IPv4 coverage. Cold-restart recovery was Partial;
  permission/reconnect recovery was Complete. Exact failed methods, when present,
  were captured before another scan.
- The cold-restart scan reproduced both known native resolution failures:
  `_hap._tcp` and `_http._tcp` registered but supplied no addresses within either
  bounded attempt of about four seconds. This is an unresolved discovery limit,
  even though the lifecycle and permission assertions passed.
- The original appearance, optional permission grants and unfiltered fresh list
  were restored. No app code, signing identity or APK bytes changed for these
  finalization checks. Clipboard mutation and physical coverage of every text
  scale/orientation were not repeated; their earlier emulator coverage remains.

Capture attempts are part of the record: a fast diagnostic reader missed nine
interrupted rows in the Home scan. The unchanged scan was retained, the host
reader stopped, and slow reverse traversal captured every required row. An initial
permission-message check preceded the controlled Scan-button request; after the
user denied that actual Android prompt, the message check passed. These are test
setup/capture corrections, not claimed application repairs.

Real Thread-mesh validation and broader router/device coverage remain. A Complete
scan means the planned checks completed, not that every device can be detected.
Play App Signing enrollment, a verified App Bundle pipeline and Play listing /
privacy requirements remain separate release work.

## Public package

The accompanying source package identifies its exact Git commit and contains
project source, build/test instructions, upstream notices, Kotlin standard
library sources and the public IEEE inputs needed to regenerate the bundled
vendor asset. Original project code is GPL-3.0-only; retain the separate upstream
licenses in NOTICE and the Android third-party notices.

No signing key, password, private backup receipt, household inventory, personal
screenshot or raw phone capture belongs in the public package. SHA256SUMS uses
portable filenames. This is a development pre-release for feedback, with the
remaining discovery and hardware limitations stated above.
