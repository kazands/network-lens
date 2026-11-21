# Network Lens 0.3.34 verification

Prepared on 2026-11-20. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **62**.

## Reviewed implementation

All application screens, menus and dialogs now use Jetpack Compose. The lazy device list adds category filters, larger icon plates and stable list restoration. Device details retain grouped addresses, reported fields, recognition explanations and complete copy output. Wider screens can show list and details together. Scan controls and lifecycle status share one panel; scan details uses a bottom sheet.

The existing backend still owns discovery, selected-network scope, current-response presence and recognition. Navigation, appearance and results stay in RAM; saved-state bundles are empty. Every Compose window excludes automatic content capture and autofill while preserving screenshots, accessibility and explicit sharing. The updater remains visible across network changes. Bounded nonblocking report writes enforce revocation and the stream deadline, including on Android 8. A typed cancellation boundary prevents late socket registration from escaping an enrichment worker.

The manual Envisalink action and dedicated TPI reader are removed. No replacement identification probe is claimed. Legacy matching vocabulary remains for interpretation tests.

A fresh independent Deep kaz-code-review approved the complete implementation with no substantive findings. Release preparation changes version metadata and documentation, plus the release-independent updater test assertion described below. All **54 host suites**, generated-data checks and Python prechecks passed on this release preparation. The first host attempt was blocked by the sandbox's loopback restriction; the permitted rerun passed.

## Build and artifact checks

Development, public-signed and instrumentation direct builds passed. Packaged permission and security audits passed for both application APKs. Runtime hashes, class/native inventories, original notices, 16 KB ELF/ZIP alignment and the pinned public certificate were verified. The eight existing permissions and three reviewed manifest components remain; no CI was added.

All **107 non-manifest payload entries** match the approved implementation candidate. All **108 non-signature payload entries** match between development and public APKs. The only payload change from the reviewed candidate is `AndroidManifest.xml`, for version 0.3.34/build 62.

## Exact versioned emulator checks

Both isolated emulators verified installed application and instrumentation hashes. Every successful result below uses the same versioned development APK. The broad matrices used instrumentation `4a082d403a74f7258ebd5140bd4eb4056ceeafb53aa30f55e2826b195bf96b5a`. Their updater scenario initially failed because its expected label hard-coded 0.3.33. The corrected test reads the installed package version and passed the complete `app-updates` mode on both APIs using instrumentation `eed0ef6a42889330b9688c0827c432fe2263179072d40b54737c9152429a2761`. Other modes were not rerun solely for that test-only correction. The table uses the corrected updater results; failed partial attempts are not counted.

| Mode | API 26 | API 37 |
| --- | ---: | ---: |
| `compose-ui` | 146 | 159 |
| `compose-typography` | 1422 | 1451 |
| `compose-reports` | 157 | 157 |
| `compose-update-network` | 43 | 43 |
| `compose-privacy` | 109 | 203 |
| `compose-list-anchor` | 30 | 30 |
| `compose-icmp-errors` | 20 | 16 |
| `icmp` | 31 | 65 |
| `scan-ownership` | 21 | 21 |
| `app-updates` | 405 | 405 |
| `device-identities` | Not run | 1088 |
| `recog` | Not run | 231 |
| `session-only` | 37 | 36 |
| `session-cold` | 5 | 5 |
| `permission` | Not run | 22 |
| **Total** | **2426** | **3932** |


Total: **6358 passing Android assertions across 27 mode results**. Modes overlap; these are not unique scenarios or product-accuracy measurements. The report tests include the full 30-second stalled-reader deadline. Layout checks use real Compose semantics and measured text in both themes, narrow/wide layouts and 200% text. List-anchor tests measure row positions after insertions, removals and Activity recreation. Privacy checks exercise the pinned capture manager with an in-memory recipient and a positive control, not an OEM capture service. Network-change tests use synthetic connections and updater/cache state, not real roaming or GitHub transfers.

Android 8's native ICMP path reports a platform permission restriction; its checks verify TCP fallback and failure presentation, not successful native echo. API 37 also exercises native replies and injected failures. The permission mode passed Android's actual local-network grant journey. In-app installer tests stop at pending user approval.

The exact public APK separately passed **23 signed-upgrade and launch checks** from public 0.3.33 on an isolated API 37 emulator. Installed bytes, pinned certificates, versions, UID, existing runtime grants and waiting for a user-started scan were verified. No physical phone was operated.

## Artifact identity

- Public APK SHA-256: `b8e19f0133eb1cadb56ddacf37ed3ce9431e695c973a4815d94576e8c2faf40b`.
- Development APK SHA-256: `dc5917119dd34106d2b3ee3ae9c7e033549f58b8e99eee240fd506bb53346b5f`.
- Final instrumentation SHA-256: `eed0ef6a42889330b9688c0827c432fe2263179072d40b54737c9152429a2761`.
- Broad-matrix instrumentation SHA-256: `4a082d403a74f7258ebd5140bd4eb4056ceeafb53aa30f55e2826b195bf96b5a`.
- Reviewed implementation candidate SHA-256: `50c3b79c6157cb7a9e293b48fa5435435c72b318f5d2f3e35699089aba4fbbae`.

The public certificate remains `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`. The unshrunk APK is about 15 MB and includes four native ABIs; the earlier View-based APK was about 4 MB. The source archive records the merged commit/tree and per-file hashes, preserves public regeneration inputs and notices, and excludes private material. Asset hashes are supplied in `SHA256SUMS`.

## Limits and required manual checks

Physical Pixel/ARM64 execution, scrolling, gesture navigation, TalkBack, the optional Wi-Fi-name permission journey, receiving-app compatibility, low-memory performance, real network roaming and OEM content-capture integration remain unverified. Live GitHub download and user-approved in-app installation were not performed by these checks. The Gradle IDE APK path remains separate from the verified direct build.

The reported Google Play Protect classification remains unresolved. Emulator installation and signature verification do not establish Google clearance. Keep Play Protect enabled.

This interface release does not establish improved recognition for silent devices, complete household discovery or physical /22 timing. It adds no permissions, cloud recognition, saved inventory, background monitoring or device-control commands. Earlier candidate results remain in [Compose implementation verification](../COMPOSE-VERIFICATION.md); they are separate from this version's checks.
