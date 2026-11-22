# Network Lens 0.3.37 verification

Prepared on 2026-11-21. Development preview for Android 8.0+; target API 37.
Application ID: `io.github.kazands.networklens`. Version code: **65**.

## Reviewed behavior

LAN discovery admits responding IPv4 endpoints on the selected Wi-Fi and eligible scope. IPv6 discovery transports, routed accessory admission and the IPv6 worker lane have been removed. IPv6 parsing/redaction in returned text and normal HTTPS updates remain supported. Wi-Fi without IPv4 displays an explicit requirement instead of an empty successful scan.

Native resolution retains its original four-second deadline so AAAA-first/A-later updates can still queue IPv4 confirmation. Permanently IPv6-only services exit cleanly at window expiry. Current-response identity, unknown fields, cancellation, scope binding and RAM-only state remain intact. Typed Android refusals/routing errors are refined before read-only retry decisions.

Independent Deep reviews and coordinator verification resolved the premature native-callback termination and stale scan-range help. The final implementation review approved the changes with no substantive findings. Earlier implementation matrices and the synthetic dual-stack comparison are recorded separately in [IPv4 scope](../reference/IPV4-SCOPE.md); they are not claimed as tests of this numbered APK.

## Build and host checks

All 55 host suites, Python diagnostics and generated-data/coverage checks passed immediately before release preparation, including all eight previously sandbox-blocked loopback suites. The numbered release changes metadata/documentation only.

Development, public-signed and instrumentation direct builds passed. Packaged permission/security audits passed for both app APKs. Signing preflight verified the pinned public identity and recovery receipt; the isolated signing integration harness passed **116 assertions**. No signing identity was replaced.

All **107 non-manifest payload entries** match the approved 0.3.36-labelled implementation candidate. Only the version manifest changed. All **108 non-signature payload entries** match between the development and public APKs.

## Exact versioned Android checks

Guarded isolated emulators verified their identity and the installed app/test APK hashes. The exact versioned development APK passed all 12 selected mode results, totalling **1,034 assertions**. Counts can overlap; they measure synthetic behavior, not physical devices or discovery accuracy.

| Mode | API 26 | API 37 |
| --- | ---: | ---: |
| `ipv4-scope` | 47 | 64 |
| `service-retries` | 210 | 213 |
| `resolver-upgrade` | Not run | 23 |
| `engine-upgrade` | Not run | 16 |
| `scan-ownership` | 21 | 21 |
| `freshness-fixes` | Not run | 92 |
| `compose-ui` | 146 | 159 |
| `permission` | Not run | 22 |
| **Total** | **424** | **610** |


## Public signed update

The exact public-signed APK passed **23 update/launch checks** from public 0.3.36 on the isolated API 37 emulator. Both certificates and installed bytes were verified. The update retained the app UID and all three established local-network/location runtime grants. Launch waited for a user-started scan. These checks use `adb install -r`, not the live GitHub in-app update journey.

## Artifact identity

- Public APK SHA-256: `daae30b38378e644ae3b20eda9e04e144ef7088964c15b0642c5d3a0242e6f49`.
- Development APK SHA-256: `8cfc920a68d543de6a04641e2c1ab1e398f22dabad8feaa88139992f5e5f6219`.
- Instrumentation SHA-256: `51413153c8224f96e60d45a2799f94aa577d68ad15fab069cd1f05c154888c0b`.
- Approved implementation candidate SHA-256: `bedfa8b55c31864117140997a510fc2fd0662771b3a607a0b03b8b942151f22e`.
- Public certificate SHA-256: `5b818d9d22d7e9fc93eaee23bfe4adb94c42c5e9f8dce2e98f0d78960c035ae3`.

## Limits

IPv6-only devices, routed Thread endpoints and identity clues available only through IPv6 are excluded. Physical before/after household recall, scan timing, ARM64 performance, low-memory behavior, TalkBack, optional Wi-Fi-name permission flow and live GitHub in-app installation remain unverified. Emulator checks are synthetic and do not establish a complete inventory. No physical device was operated. The earlier Play Protect classification remains unresolved; signing does not establish Google clearance.

The matching source package identifies the merged commit/tree and includes required public inputs, per-file hashes and original notices. Credentials, local caches and household evidence are excluded. Assets are published with SHA256SUMS.

[Build instructions](../BUILDING.md) · [Previous release verification](VERIFICATION-0.3.36.md)
