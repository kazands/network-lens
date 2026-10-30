# Release signing

Network Lens uses three separate identities. The existing development/test identity is preserved. Public APKs use the app-signing identity pinned in `android/release-signing.json`. The upload identity is reserved for future Google Play App Bundle submissions; it does not sign direct-download APKs.

Only public certificate fingerprints, role names and aliases belong in Git. All private keys, password files and recovery receipts stay outside Git under `.tools/release-signing/`. This directory contains active credentials, not disposable tool caches. Never delete it during cleanup.

## Private files

| Role | Keystore | Password file | Alias |
| --- | --- | --- | --- |
| Public app signing | `network-lens-app-signing.p12` | `network-lens-app-signing-password` | `network-lens-app` |
| Google Play upload | `network-lens-upload.p12` | `network-lens-upload-password` | `network-lens-upload` |

Keys are independent RSA 4096 keys in PKCS#12 containers with separate generated passwords. Directories must be owned by the current user with mode `0700`; private files must be owned by that user with mode `0600`. The scripts use password-file inputs and never print password values or raw tool diagnostics on failure.

`app-certificate.pem` and `upload-certificate.pem` in the private directory are public certificates, not private keys. The upload certificate will be needed when registering the upload identity with Google Play. They can be regenerated from their keystores.

## Existing checkout and recovery

This project has already initialized its public identities. Restore the four private files from their personal Vaultwarden entries when moving to a new computer. Do not initialize replacement identities or change pinned fingerprints to make a different key pass.

Keep both the keystore and password file for each role. Vaultwarden server backups must include attachment files as well as the database. Keep a separate encrypted recovery copy outside that server, and protect account recovery access independently.

Inspect restored credentials without signing an artifact:

```sh
python3 scripts/release-signing.py inspect
```

To verify backups, download independent copies of all four files into a separate owner-only directory, set each file to mode `0600`, then run:

```sh
python3 scripts/release-signing.py verify-backup --restored-directory /private/path/to/downloaded-copies
python3 scripts/release-signing.py check
```

Verification compares each downloaded file with its original, exercises each restored private key with a signed certificate request verified by the JDK, and matches its certificate against the public identity. It writes `.tools/release-signing/backup-verification.json`. Original files and hard links to them are rejected as recovery copies. Remove temporary downloaded copies after verification. The receipt records only checks, certificate fingerprints and encrypted-keystore file hashes; it contains no passwords or key contents. It verifies those copies, not the server's complete disaster-recovery process.

If a key file changes, its previous receipt no longer permits signing. Recover and verify the intended credentials. Never resolve a signing failure by silently generating another key.

## Build a direct-download APK

Use the full JDK and pinned SDK described in [BUILDING.md](BUILDING.md):

```sh
bash scripts/build.sh --release
```

The command checks the app key, pinned certificate, owner-only permissions and backup receipt before build work or downloads. It uses `.tools/build-release/` and writes `artifacts/release/Network-Lens-0.3.9.apk`, its SHA-256 file and permission/security audit outputs. It does not overwrite the development APK or use the upload key. APK signature schemes v2 and v3 cover the supported Android 8.0+ range; v1 and v4 are disabled for this path.

The ordinary `bash scripts/build.sh` and Android instrumentation builder continue to use the original development key and existing output locations. Production credentials are not passed to instrumentation or ordinary tests. The signing integration harness generates isolated synthetic credentials instead.

```sh
bash scripts/test-release-signing.sh
```

Build the development APK first, since the harness uses its application payload as a signing fixture. It tests certificate separation, preserved test credentials, explicit initialization, private permissions, backup admission, invalid credentials/metadata, output preservation and exact application payload equality. It does not read the real production keys or operate a phone.

## Initialization is explicit and one-time

`python3 scripts/release-signing.py init` exists for the initial maintainer setup when both the public identity file and private release directory are absent. It refuses existing or partially initialized state. Builds never invoke it. A partial initialization is preserved for inspection/recovery; do not delete private material or rerun key creation blindly.

## Installation and Google Play

The public app key differs from the existing test key. Changing from the current test installation to a public build requires a one-time uninstall/reinstall; a normal update is expected to be rejected. This setup does not uninstall the user's test app. Future public builds must retain the pinned app identity and suitable version codes.

For GitHub and Google Play to share the app identity, enroll the app-signing key using Google's official encrypted key-transfer process during Play App Signing setup, then register the separate upload certificate. Never attach a private key or password to a GitHub release or issue. See [Android app signing](https://developer.android.com/studio/publish/app-signing).

This is a direct APK signing pipeline and upload-key preparation. A verified Play App Bundle build and Play Console enrollment remain separate work; Gradle metadata alone is not a verified release-publication path. No release, tag, Play listing or public binary is published by these scripts. Signing does not establish production readiness or resolve discovery limitations in [STATUS.md](STATUS.md).
