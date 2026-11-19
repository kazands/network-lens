# Network Lens 0.3.23 verification

Prepared on 2026-11-18. Android development preview for home-network testing.

## Identity and changes

- Application ID `io.github.kazands.networklens`; version **0.3.23 / code 51**.
- Android 8.0+ (API 26); target API 37. Public signing identity is unchanged.
- Wider list, detail and settings layouts; compact settings actions with at least
  48dp touch targets. Scalable text, IPv4/IPv6 ordering and Back position remain.
- Settings retains the Options menu. Shared dialogs use an opaque card surface
  and a stronger backdrop to separate them from the page behind them.
- Update progress retains text/action views, focus and scroll position. Unrelated
  page refreshes retain the update window; appearance/configuration changes replace
  it safely. Download validation, cancellation and Android approval are unchanged.
- Scan percentages describe the first address pass only. An animated identifying
  phase follows while discovery and lookups continue. Final failure/coverage
  reporting remains available in Scan details.
- Grouped addresses share an identification explanation only when their independent
  product evidence and explanations agree. Each address retains its unknown values,
  sources, technical details and copied observations.
- No new permission, stored inventory, analytics, cloud recognition or background
  scanning. Existing device-recognition coverage is retained.
- Release notes inside the app are not implemented in this release.

## Review and candidate verification

Deep kaz-code-review and a fresh independent reviewer approved the entire
interface changeset without substantive findings. Before versioning, **3,446
Android checks passed**: 2,706 on API 37 across ten modes and 740 on API 26 across
four modes. Both appearances, normal/200% text, 320dp/411dp typography, all nine
update states, cancellation, installer continuation and navigation were covered.
See the [interface surface review](UI-SURFACE-REVIEW.md) for counts and limits.
The full host suite was not rerun for these Android-only presentation changes.

Release preparation changed version metadata, documentation and download links.
All **167 reviewed Kotlin application/test sources** remain unchanged.
All **48 payload entries except the manifest** match the reviewed candidate.

## Exact versioned artifacts

- Direct development, public-signed and instrumentation builds passed, including
  packaged permission/security audits.
- Development APK SHA-256: `876e7687ee9d3d592a24002a6316f82e0f5964b743e3aac8e76b57cf9a3d5fbc`.
- Public APK SHA-256: `bb1e953ee489d895a3311c7baa527b8f9370f44e91d8e73a4ea297e6d94b384e`.
- Instrumentation APK SHA-256: `5b10db32c73cf00b62b50f1a0501b412b781e56c8183d98eb542e3fddc8f1cb3`.
- All **49 non-signature payload entries** match between development and public APKs.
- Release-signing tests passed **116 assertions** with isolated test credentials.
- The versioned development APK passed **742 Android checks on API 37** across
  `app-updates` (502), `options` (128), `scan-details` (74) and `navigation` (38).
  This was not an uninterrupted passing matrix: Options initially failed its
  scroll-to-action assertion after returning from Android Settings, then passed
  unchanged in isolation. The exact timing trigger remains unconfirmed. An
  earlier emulator boot also required a restart after System UI failed before
  app testing began. Installed application/test hashes were verified by the runner.
- The exact public-signed APK passed **23 in-place update/launch checks** from
  public 0.3.22 on API 37. The signing certificate, installed bytes, version,
  application UID and existing runtime permissions were verified. The updated
  app opened normally and waited for a user-started scan.

## Distribution and remaining checks

The release includes the signed APK, matching tagged source package, verification
notes and SHA256SUMS. The source manifest identifies the exact Git commit/tree,
per-file hashes and separately identified public regeneration inputs. Private
signing material, household data and local caches are excluded.

No physical phone was operated for this release. Device-specific graphics
artifacts, physical TalkBack and the live in-app download/approval journey still
need physical verification. The UI fixes do not claim to improve discovery
completeness or resolve unavailable device metadata. No GitHub CI was added;
verification is performed locally. This remains a development preview.
