# Wi-Fi irrigation recognition

The local network name reader is original GPL-3.0-only code. It uses no Fing code, database, cloud API or Bluetooth access.

## Evidence and display

Only endpoints with a current response are eligible. The reader selects the first configured DNS resolver that is both private/link-local and on-link on the selected Wi-Fi. It does not substitute public DNS or guess a gateway. Each endpoint gets at most one reverse query and one forward query, a shared 700 ms deadline, a maximum 350 ms receive per query, and a 4,096-byte response cap. At most 128 endpoints are admitted per scan through the existing four-worker name lane. Exhaustion is reported; no additional scan-time budget is invented.

Resolver eligibility is checked before admission. Public-only, off-link or absent DNS servers consume no lookup capacity and cannot cause a false work-limit warning, including when more than 128 addresses respond. The cap still applies when an eligible resolver exists.

Replies must match the connected DNS peer, random transaction ID, exact question, class and answer owner. Multiple PTR names are ambiguous. Bounded in-packet CNAME chains may confirm the forward address; loops, conflicting aliases, malformed compression, oversized records and truncated UDP answers are rejected. TTL-zero records are usable for the current transaction; there is no cross-lookup or cross-scan cache.

Up to four returned names are retained as private diagnostic evidence, including candidates rejected by forward confirmation. Additional names have an explicit omission count. Lookup transitions are journalled; the outcome and retained names appear in technical details and the troubleshooting report. Arbitrary DNS authority/additional records and raw packet bodies are not retained.

Reverse/forward agreement verifies only DNS consistency, not the current occupant's physical identity. A router can retain matching stale records in both directions. Suggestions therefore remain **Likely**, yield to current reported model/manufacturer/type evidence and product/integration matches, and never create presence or grouping. Reported identity and primary type stay unknown when the peer did not supply them. Lookup outcomes are retained separately, including no resolver, negative response, mismatch, ambiguity, malformed response, timeout, cancellation and admission limits.

| Pattern | Additional condition | Tentative display |
| --- | --- | --- |
| `hydrawise-` plus exactly four hexadecimal digits, optionally followed by a DNS suffix | If a current adapter MAC is available, its last two bytes must agree | Likely Hunter Hydrawise; irrigation controller |
| `bhyve-bh1-` plus a 4–32 character alphanumeric suffix, optionally followed by a DNS suffix | No stronger reported identity/type evidence | Likely Orbit B-hyve hub; irrigation hub |
| Other bounded `bhyve-` names | Independently observed interface MAC starts `44:67:55`; no role/model inferred | Likely Orbit B-hyve; device family |

The BH1 name pattern has narrower evidence than the Hydrawise pattern and is intentionally tentative. It is not a claim about every firmware generation. MAC prefixes alone never identify a specific B-hyve product; Orbit also sells hubs and sensors. Component-vendor prefixes are not mapped wholesale to Hunter.

The reader does not authenticate, control irrigation, query cloud accounts, capture DHCP traffic or enumerate Bluetooth timers. A hub's child timers are not fabricated as Wi-Fi endpoints. Unsupported devices can remain unnamed.

## Research provenance

- [Pydrawise discovery reference](https://pydrawise.readthedocs.io/en/latest/reference/discovery/) documents Hydrawise's hostname suffix. Its Microchip prefix observation is not universal, and this implementation does not require it. Its blanket local-API statement also does not apply to every historical firmware; [OpenHAB documents a legacy local interface](https://www.openhab.org/addons/bindings/hydrawise/).
- [Hunter Wi-Fi specifications](https://www.hunterirrigation.com/support/hydrawise-wi-fi-specifications) document the cloud-oriented connection requirements and no required inbound ports.
- [Orbit's pairing guidance](https://wisdom.orbitonline.com/how-to-pair-your-b-hyve-hose-faucet-smart-watering-timer-bluetooth/) documents the `44:67:55` prefix. The existing bundled IEEE registry independently identifies that prefix as Orbit Irrigation.
- [Le Sanctum's device-identity documentation](https://www.sanctum.run/operations/device-identity/) records `bhyve-bh1-*` as a DHCP hostname example. It supports a research lead for this specific hub name, not universal B-hyve LAN discovery. No source or data was incorporated.
- [B-hyve Home Assistant](https://github.com/sebr/bhyve-home-assistant/tree/cda35327559c42411332b81cd5b21795ea4c24f2) (MIT) uses cloud integration. It does not establish an unauthenticated LAN model reader. No code was incorporated.

Public documentation and source were inspected on 2026-11-19. Synthetic protocol and Android regression fixtures cover the evidence boundaries. B-hyve physical hardware remains unverified; no universal B-hyve public UDP/HTTP identity endpoint was established by the research. This feature has no Bluetooth permission or dependency.

## Development verification

On 2026-11-19 the direct app and Android-test builds passed. After review fixes, the full host runner passed all 54 Kotlin suites and its Python checks, including 13 instrumentation-startup safety assertions, 119 local-name assertions and 10,000 malformed forward-DNS packets. The updated development APK passed 42 `network-names` checks and 39 `kidde` checks on an isolated API 37 emulator, with installed APK hashes verified by the runner. The expanded DNS checks cover 129 responders without an eligible resolver and genuine eligible-resolver exhaustion. Startup tests verify rejection before appearance changes or legacy-preference migration. Recognition coverage, repository and whitespace checks passed. The manifest contains no Bluetooth permissions.

Earlier feature verification passed 235 `recognition-report` checks on API 37; that mode was not rerun for these two review fixes.

These results cover synthetic resolver selection, fresh-response admission, duplicate suppression, cancellation, family suggestions, current friendly-name preservation, stronger-evidence precedence, grouping exclusions, diagnostic retention and privacy. They do not establish real B-hyve compatibility, Android Wi-Fi DNS transport on a physical phone, or recognition accuracy across routers. No release or physical-device installation was performed for this change.

The versioned release checks are recorded in [0.3.31 verification](../VERIFICATION-0.3.31.md).
