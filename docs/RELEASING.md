# Release procedure

This procedure publishes a numbered GitHub development preview. It does not enrol the app in Google Play.

## Prepare the candidate

1. Finish the code review and resolve substantive findings.
2. Update the version name and monotonically increasing version code in the Android manifest and Gradle metadata. Update the APK filename in `scripts/build.sh`.
3. Write concise user-facing changes in `docs/CHANGES.md`. Update the README download link, badge, getting-started guide and current status. Keep implementation detail in reference pages.
4. Run the relevant [build and test checks](BUILDING.md). Record results and exact hashes in `docs/VERIFICATION-<version>.md`; never copy earlier results as though they ran on the new APK.

## Sign and verify

```sh
python3 scripts/release-signing.py check
bash scripts/build.sh --release
```

Use the existing pinned app-signing identity and its verified recovery receipt. The public key, development key and future Play upload key have distinct roles. Never create a replacement key to make a release pass. See [Signing reference](reference/RELEASE-SIGNING.md).

Compare the development and public APK payloads, verify the public certificate and run an in-place upgrade from the previous public release on an isolated emulator. Verify the installed bytes, version, UID, permissions and launch state. Physical-device checks remain separate.

## Merge and publish

Create a PR with the problem, final behavior, implementation, performed tests, risks and manual checks. Merge only the reviewed head after checking repository requirements. Do not describe absent CI as passing CI.

Tag the merged commit and publish the public-signed APK with matching source, verification notes and `SHA256SUMS`. The source package must identify the commit/tree, include required public regeneration inputs with their notices and checksums, and exclude credentials, personal data and local caches.

Use clear release notes: what changed, how to update, and material limits. Verify the uploaded asset digests and download links. Keep the numbered development-preview designation; the in-app updater includes these previews.

## Clean up

Return to the updated main branch and remove the merged working branch locally and remotely. Stop task-owned emulators or temporary servers. Preserve ignored signing material, recovery receipts and useful release evidence. `.tools/release-signing/` contains active credentials and is never disposable cleanup.

Archive past verification reports when they stop describing the current release. Keep [the documentation home](README.md) focused on current use and development.
