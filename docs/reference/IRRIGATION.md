# Local network name diagnostics

Local network names are troubleshooting evidence only. The earlier Hydrawise and Orbit B-hyve name-based recognition rules have been removed. No router-name pattern supplies a product, family, manufacturer, primary type or list title.

## Lookup boundaries

Only current responding endpoints are eligible. The first configured DNS resolver that is both private/link-local and on-link on the selected Wi-Fi can be queried; no public resolver or guessed gateway is substituted. Each endpoint has one reverse query and one forward query, a shared 700 ms deadline, at most 350 ms per receive and a 4,096-byte response cap. At most 128 endpoints are admitted through the existing four-worker name lane. Resolver eligibility is checked before admission, so absent or unsupported DNS configurations consume no capacity and cause no false work-limit warning.

Replies must match the peer, transaction ID, question, class and answer owner. Multiple PTR names are ambiguous. Bounded in-packet CNAME chains may corroborate the forward address; loops, malformed compression, conflicting aliases and truncated answers are rejected. TTL-zero records are usable in the current transaction; no names are cached between scans.

Up to four returned names and explicit omission counts are retained privately. Lookup outcomes and names appear under **Show technical details**, copied details and troubleshooting reports; names are masked by default. DNS agreement does not establish the current occupant's identity. No name establishes presence or grouping, changes the searchable identity, fills reported fields or overrides current device evidence.

This code is original GPL-3.0-only and uses no cloud recognition, Bluetooth access, account credentials or irrigation controls. Hydrawise and B-hyve devices need independently supported device identity responses to receive a product match.

## Earlier irrigation research

- [Pydrawise discovery reference](https://pydrawise.readthedocs.io/en/latest/reference/discovery/) documents Hydrawise's hostname suffix. Its Microchip prefix observation is not universal, and the removed matcher did not require it. Its blanket local-API statement also does not apply to every historical firmware; [OpenHAB documents a legacy local interface](https://www.openhab.org/addons/bindings/hydrawise/).
- [Hunter Wi-Fi specifications](https://www.hunterirrigation.com/support/hydrawise-wi-fi-specifications) document the cloud-oriented connection requirements and no required inbound ports.
- [Orbit's pairing guidance](https://wisdom.orbitonline.com/how-to-pair-your-b-hyve-hose-faucet-smart-watering-timer-bluetooth/) documents the `44:67:55` prefix. The existing bundled IEEE registry independently identifies that prefix as Orbit Irrigation.
- [Le Sanctum's device-identity documentation](https://www.sanctum.run/operations/device-identity/) records `bhyve-bh1-*` as a DHCP hostname example. It supports a research lead for this specific hub name, not universal B-hyve LAN discovery. No source or data was incorporated.
- [B-hyve Home Assistant](https://github.com/sebr/bhyve-home-assistant/tree/cda35327559c42411332b81cd5b21795ea4c24f2) (MIT) uses cloud integration. It does not establish an unauthenticated LAN model reader. No code was incorporated.

Public documentation and source were inspected on 2026-11-19. The name-based product rules from 0.3.31 are no longer active. B-hyve physical hardware remains unverified; no universal B-hyve public UDP/HTTP identity endpoint was established by the research. This feature has no Bluetooth permission or dependency.
