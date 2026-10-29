# Contributing to Network Lens

Thank you for helping improve Network Lens. Open a focused issue or pull request on GitHub. For large behavior changes, discuss the problem and proposed scope in an issue first.

## Keep the product focused

Network Lens is a foreground Wi-Fi device finder. Preserve fresh-response requirements, current-session-only state, bounded cancellable discovery, selected-network binding and honest Complete/Partial reporting. Do not add saved history, background monitoring, analytics, accounts, cloud recognition or permissions without prior discussion.

Never promote cached advertisements, old router names or previous results into proof of current presence. A device that does not answer is not necessarily offline. Keep model/manufacturer/vendor claims tied to their actual source.

## Validation

Follow [BUILDING.md](docs/BUILDING.md). Run relevant host suites and Android checks. State the exact checks run and any limitations in your pull request. Do not claim physical-device, Thread or API coverage from an emulator-only test. The emulator scripts deliberately refuse physical-device serials.

Describe the problem, the resulting behavior, validation and remaining risks. Use small changes and preserve existing navigation, appearance, accessibility and cancellation behavior.

## Protect private data

Use synthetic device names and addresses in fixtures. Do not commit signing keys, credentials, device serials, household inventories, SSIDs, packet captures, APKs from other applications or personal screenshots. Check the staged diff before committing. Local tools and evidence are ignored; do not force-add them.

Report security issues using [SECURITY.md](SECURITY.md).

## Licensing

By intentionally submitting a contribution for inclusion, you agree to license it under GPL-3.0-only, except clearly identified third-party material under compatible terms. Submit only material you have the right to contribute, and retain upstream notices and provenance. No separate contributor license agreement is required. This policy does not transfer copyright ownership.
