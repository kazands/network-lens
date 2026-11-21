# Network Lens 0.3.35 verification

Prepared on 2026-11-20. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **63**.

## Reviewed behavior

Unresolved current responders can receive bounded local HTTP, presented-certificate, SSH and direct LLMNR hostname checks. Selected service clues remain separate from hardware model, manufacturer, MAC and grouping evidence. Corroborated Home Assistant, Pi-hole, Tasmota and OctoPrint pages can identify software services without identifying their host hardware. Existing equipment root requests are reused. Supported readers record queue wait, remaining budget at start and request duration.

Technical details and explicit copies show selected clues. Troubleshooting reports mask public labels by default and offer an explicit names/addresses/public-labels option. Counts-only reports retain fixed outcomes. Credentials, cookies, nonces, raw certificates, keys and unreviewed bodies are excluded. Discovery remains selected-Wi-Fi-bound, user-started and memory-only, with current responses required for presence.

A fresh independent Deep kaz-code-review covered the complete 35-file implementation and approved it with no substantive findings. Release preparation changes version metadata and documentation. All **107 non-manifest application payload entries** match the reviewed implementation candidate. The only payload difference is `AndroidManifest.xml`, for 0.3.35/build 63. All **108 non-signature payload entries** match between development and public APKs.

## Build and host checks

Development, public-signed and instrumentation direct builds passed. Permission/security audits passed for both application APKs, including the existing permission/component inventory, pinned runtime/native hashes and alignment checks. Signing preflight verified the existing public identity and recovery receipt. No replacement key or permission was introduced.

All **55 host suites** and **29 Python public-trial tests** passed during release preparation. The host public-reader suite contains 2,080 assertions. Generated-data and recognition-policy checks passed. The host trial's real-certificate tests use in-memory synthetic fixtures and actual OpenSSL, including URI/email marker rejection, retained DNS/IP fields and zero-label availability. The bounded structural cases include 1,000 malformed inputs. Repository guardrails, Python syntax, documentation links and diff checks passed; guardrails are not a guarantee that private data is absent.

## Exact versioned Android checks

Both guarded isolated emulators rejected physical targets and verified the installed application and instrumentation hashes. These results use the exact versioned development APK and instrumentation listed below.

| Mode | API 26 | API 37 |
| --- | ---: | ---: |
| `public-identity` | 95 | 95 |
| `local-metadata` | 61 | 61 |
| `scan-ownership` | 21 | 21 |
| `compose-reports` | 157 | 157 |
| `device-identities` | 1120 | 1088 |
| `recog` | 250 | 231 |
| `app-updates` | 405 | 405 |
| `permission` | Not run | 22 |
| **Total** | **2109** | **2080** |

Total: **4,189 successful Android assertions across 15 mode results**. Counts overlap and can vary with synthetic queue scheduling; they are not distinct products or accuracy measurements. Checks cover public-reader bounds and terminal ownership, shared-root scheduling, retained report privacy, existing device identities and catalogue behavior, updater state, and Android 17's actual local-network permission journey. Loopback TLS observation rejects its handshake; ordinary HTTPS rejects the self-signed fixture. The overlong escaped-subject regression extracts a synthetic certificate directly under a worker deadline, without serving it over the network.

The exact public APK separately passed **23 signed update and launch checks** from public 0.3.34 on an isolated API 37 emulator. Both certificates and installed APK bytes were verified. Updating retained the application UID and all three established local-network/location runtime grants. The app launched and waited for a user-started scan. These checks use `adb install -r`, not the live GitHub in-app download/install path.

## Artifact identity

- Public APK SHA-256: `f9bec2d6471c4e035bb7bad3349768ec18103acabe36130682c12421db2abd4a`.
- Development APK SHA-256: `218f243bdc722028315eb638db83221f6e41c60a811b956c3f6c4397f07b6fd8`.
- Instrumentation SHA-256: `3208140b7fb614cea5d3caaf9ad007d5453a67e5e56cf40c37ebc430f6c8623a`.
- Reviewed implementation candidate SHA-256: `c4e823328cef518f6fabccc9ca922d6b04151d2dceb734d6b9264e45a10eb085`.
- Public certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

The APK remains about 15 MB with four native ABIs. The source package identifies the merged commit/tree, per-file checksums and required public regeneration inputs/notices. Private signing material, local caches and household evidence are excluded. Release assets have matching `SHA256SUMS`.

## Limits and manual verification

Physical recognition gains, firmware compatibility, ARM64/low-memory performance, TalkBack, the optional Wi-Fi-name permission flow, receiving-app behavior and OEM capture integration still need broader testing. No household scan or physical phone operation was performed. Emulator success does not establish a complete inventory or improved recognition accuracy. Devices exposing no supported identity can remain unnamed; public labels do not prove a product or exact generation.

Live GitHub download/user-approved in-app installation and the Gradle IDE build remain outside these checks. The prior Play Protect classification remains unresolved; signing and emulator installation do not establish Google clearance. Keep Play Protect enabled. No CI, saved inventory, background scan, cloud recognition or device-control commands were added. Credential-based SNMP and production IPv6 multicast probing remain follow-up work.

Earlier candidate results are in [implementation verification](PUBLIC-IDENTITY-VERIFICATION.md). The previous release's exact checks remain in [the 0.3.34 archive](archive/VERIFICATION-0.3.34.md).
