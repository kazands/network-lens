# Network Lens 0.3.8 verification

Prepared 2026-10-30 for a development preview. This is a sanitized summary of
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

The post-measurement physical search/Back/theme checks were not run because
the app left the foreground. Those paths passed in emulator matrices. A complete
journey on the **production-signed APK on the physical phone remains pending**;
no physical phone was reinstalled during signing preparation. Moving from the
old test identity requires a one-time reinstall and fresh permission grants.

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
portable filenames. This preview remains a draft pending its phone check.
