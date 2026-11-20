# Compose migration verification

This records the pre-release Compose candidates, labelled **0.3.33 / build
61** during implementation. They are distinct from the public 0.3.33 APK.
[Version 0.3.34 verification](VERIFICATION-0.3.34.md) identifies the release
binaries and their checks. Results below are historical synthetic runs on
isolated x86_64 emulators, never household scans or a physical phone.

The original migration build and matrix below predate the
[content-capture follow-up](#content-capture-follow-up), which records the newer
candidate and its focused checks separately.

## Implementation

- All application screens, menus, dialogs and scan details use Compose. Device
  results use a lazy list with stable keys, searchable hidden fields and category
  filters. Details retain every grouped endpoint and source-labelled observation.
- Scroll and disclosure choices remain in RAM. Activity recreation preserves
  in-process navigation; saved-state bundles are empty. Keyboard privacy flags,
  sensitive copies, foreground cancellation and export-generation fences remain.
- Catalogue completion refreshes cached presentation snapshots. Refreshing
  details retains the same Compose surface and scroll position.
- Address groups keep left-to-right order inside right-to-left layouts. Copy
  actions use the original address without display-only wrapping characters.
- The manual Envisalink action and its dedicated TPI reader are removed.
- Android 8 testing found that closing a blocking report pipe did not reliably
  stop a stalled writer. Nonblocking writes and bounded polling now enforce
  revocation and the 30-second stream deadline. Two-reader limits remain.

## Build and audits

The direct application and instrumentation builds pass with JDK 21, SDK 37,
Kotlin 2.3.21 and D8/R8 8.13.19. Application bytecode targets JVM 11, with Android
8.0 as the minimum. Compose BOM 2026.11.01 and Activity Compose 1.13.0 resolve
through a locked, checksum-verified dependency graph.

Candidate APK SHA-256:

```text
cdc0e75424f1ede01adfdd783f87d56a5b3698b893bcbc7a1e24d8d36f14f17c
```

The APK is development-signed. Its roughly 15 MB unshrunk size includes four
native ABIs; the previous UI build was roughly 4 MB. Native hashes, 16 KB ELF
load alignment and ZIP alignment pass. The permission and packaged-component
audits pass: the existing eight permissions, launcher, private installer receiver
and private report provider remain. Library startup/profile components are not
merged. Runtime classes and bundled original notices match the pinned inventory.

All **54 host suites**, generated-data checks and Python diagnostic tests pass.
Repository path/content guards and `git diff --check` pass. The eleven newly
retained Lucide originals match their recorded Git blob and SHA-256 hashes.

## Emulator checks

The runner verifies both installed APK hashes before testing. The Compose driver
uses actual semantics, actions and measured text layouts. Android 8 captures use
the actual app/dialog view when its display-capture API returns no bitmap.
Functional scenarios use a 411 dp baseline; layout checks also exercise narrow
320 dp / 200% text and a wide layout. Android 8's legacy display scaling needs
a lower test density to reach the wide layout class. Both themes are exercised.

Both final matrices pass on the APK above. The test APK SHA-256 is
`c2a7d56745750af799b1bd549ec39893cd758a9bc78a790545f4fe6d22cd3615`. Results below come from the same final application and
instrumentation binaries; installed hashes were checked on each emulator.

| Mode | API 26 | API 37 |
| --- | ---: | ---: |
| `compose-rtl` | 12 | 10 |
| `compose-ui` | 146 | 159 |
| `compose-typography` | 1422 | 1450 |
| `compose-reports` | 157 | 157 |
| `device-identities` | Not run | 1088 |
| `recog` | Not run | 231 |
| `audio-zones` | Not run | 70 |
| `app-updates` | 404 | 404 |
| `scan-ownership` | Not run | 16 |
| `scan-efficiency` | Not run | 23 |
| `session-only` | 37 | 36 |
| `session-cold` | 5 | 5 |
| `permission` | Not run | 22 |
| `performance` | Not run | 16 |
| **Total** | **2183** | **3687** |

The report checks include byte-for-byte full output, read-only and path guards,
two concurrent stalled recipients, revocation, the full 30-second stream deadline
and reuse after timeout. The installer path reaches pending user approval
without approving an installation. Layout/scroll checks use the actual Compose
roots and measured text; scroll comparisons wait for animated scrolling to stop.

## Remaining verification

Physical Pixel/ARM64 execution, real Wi-Fi recognition, gesture navigation,
TalkBack, the optional Wi-Fi-name permission journey, receiving-app compatibility and low-memory performance still need
testing. Emulator timings are synthetic measurements, not phone performance
claims. No live GitHub download or approved APK installation was performed by
these tests. Installer checks stop at Android's pending-user-approval handoff.
The Gradle IDE build remains separate from the verified direct APK path.

The reported Play Protect classification remains unresolved. These checks do not
establish Play Protect clearance or improve identification of silent devices.

See [build commands](BUILDING.md), [interface contracts](reference/INTERFACE.md)
and [report lifecycle](reference/TROUBLESHOOTING-REPORT.md).

## Content-capture follow-up

The Compose candidate now disables the runtime's virtual-content capture before
creating UI content. The Activity, dialogs, bottom sheet and overflow popup also
exclude their view roots from platform capture and autofill. No secure-window
flag is added: screenshots, accessibility semantics and explicit copy/share
actions remain available.

The direct app and instrumentation builds pass, including generated-data,
dependency, permission, component and native-library/alignment audits. Repository
guards, guards over the two new source/test files and `git diff --check` pass.
The 54 host suites were not rerun for this UI-only fix; their earlier passing
review receipt remains separate.

Follow-up application SHA-256:

```text
ff599cdbf59fdbbb38bcbdc03fd33ef0eb0791ed4e59767636b939e2ac79d47e
```

Follow-up instrumentation SHA-256:

```text
d3cc4fe64cf4735c62587250f2a0cdb85217748acd7f9c133a8d64adfe8533e3
```

Both installed hashes were verified on each isolated emulator. All five modes
passed on these binaries:

| Mode | API 26 | API 37 |
| --- | ---: | ---: |
| `compose-privacy` | 109 | 203 |
| `compose-ui` | 146 | 159 |
| `compose-reports` | 157 | 157 |
| `compose-updates` | 363 | 363 |
| `session-only` | 37 | 36 |
| **Total** | **812** | **918** |

The new privacy mode checks every separate window, Activity recreation and
screenshot availability. On API 37 it injects an in-memory recipient into each
actual Compose capture manager: no structures or text are sent while protection
is enabled. A positive control temporarily enables capture in the same main-thread
call and verifies that the manager then requests virtual structures. It restores
protection before yielding and does not retain capture payloads. Android 8 has no
platform content-capture service; that run checks compatible startup, root
autofill exclusion and absence of screenshot-blocking flags.

These are pinned-runtime boundary tests, not an OEM capture-service integration
test. No physical phone or household scan was used, and nothing was published.

## Update-window network-change follow-up

The next follow-up preserves the update dialog during Wi-Fi loss, connection
replacement and IPv6 scope withdrawal. Progress callbacks and Cancel remain
usable, and the verified-update action stays visible without reopening the
menu. Network-bound dialogs and obsolete troubleshooting previews still close.

Both direct builds and APK packaging audits pass. The final application SHA-256
is `55a14725bb0e1f301217fc00962f4158a98f84b415b7c15a370da3f8f3d58d79`;
the instrumentation SHA-256 is
`b786ca3a2e498ef17f95a751a16b0a7cfbe4213ff875f16a726b189ceef8fbc8`.
Both installed hashes were checked on each isolated emulator.

| Mode | API 26 | API 37 |
| --- | ---: | ---: |
| `compose-update-network` | 43 | 43 |
| `compose-updates` | 363 | 363 |
| `compose-reports` | 157 | 157 |
| `compose-privacy` | 109 | 203 |
| **Total** | **672** | **766** |

All four modes passed: 1,438 assertions, including 86 assertions in the new
network-change mode. That mode uses the real reconciliation method, updater
callback and Compose actions with synthetic connections, update state and cache
bytes. It checks both downloading and ready states, keeps the same dialog and
Cancel node, verifies that cached bytes are retained without a new check, and
confirms that stale scan endpoints and report previews retire. It does not
change the emulator's network, download from GitHub or approve an installation.

Repository/content guards and `git diff --check` pass. Host suites were not rerun
for this UI-only follow-up. Physical transfer interruption and real-device
network roaming remain unverified. The candidate remains unpublished.

## List restoration, ICMP and cancellation follow-up

List restoration now retains an item key and offset in RAM, resolving the key
against current results after returning from details or Settings. A removed key
falls back to a bounded current index. New scans, queries and filters clear the
anchor. Regression checks measure actual row positions after insertions,
removals and Activity recreation.

The retained native `icmp` mode now uses Compose semantics for its error sheet,
including the real failed method value in both themes. The separate
`compose-icmp-errors` fixture exercises that UI without a network probe. Legacy
dialog-field and screenshot helpers have been removed.

The first Android 8 native run exposed an uncaught cancellation exception from
a detail worker registering a socket after scan cleanup. The enrichment boundary
now consumes that expected cancellation signal; the resource owner still closes
the rejected socket. A deterministic `scan-ownership` regression pauses an actual
worker across cancellation and checks termination, socket closure and absence of
an uncaught exception. The earlier failed run is superseded by the passing matrix
below.

Final development APK SHA-256:

```text
50c3b79c6157cb7a9e293b48fa5435435c72b318f5d2f3e35699089aba4fbbae
```

Final instrumentation SHA-256:

```text
2e5d0fa959d28436273a5845031c23486e198c4d631d4e4481d8272bc040b8f8
```

Both direct builds pass, including generated-data and APK packaging audits.
Both installed hashes were verified on each isolated emulator. All eight modes
passed on these binaries:

| Mode | API 26 | API 37 |
| --- | ---: | ---: |
| `scan-ownership` | 21 | 21 |
| `compose-list-anchor` | 30 | 30 |
| `compose-icmp-errors` | 20 | 16 |
| `icmp` | 32 | 65 |
| `compose-ui` | 146 | 159 |
| `compose-update-network` | 43 | 43 |
| `compose-privacy` | 109 | 203 |
| `session-only` | 37 | 36 |
| **Total** | **438** | **573** |

The 1,011 assertions include the actual native ICMP path. Android 8 reports a
platform permission restriction for native echo; its checks verify real TCP
fallback and retained failure presentation, not successful echo support. Android
17 also exercises native replies and injected transport failures.

Repository/content guards and `git diff --check` pass. The full host suite was not
rerun in this follow-up. Physical-device navigation, TalkBack, network roaming
and OEM capture-service integration remain unverified. Nothing was published.
