# Contributing

Network Lens is for home users first, with technical evidence available for enthusiasts and network technicians. Keep changes focused on finding current responders and explaining their identity clearly.

## Before changing code

Read [project guidance](AGENTS.md) and [scan architecture](docs/reference/SCAN-ARCHITECTURE.md). Discuss large changes in an issue before implementation.

Preserve fresh scans, selected-network binding, bounded work, cancellation and cleanup. Cached advertisements are candidates, not presence proof. Keep hardware identity separate from services, and preserve unknown or conflicting values. Do not add history, cloud recognition, background monitoring or new permissions without discussion.

## Verify the change

Use the direct commands in [Build and test](docs/BUILDING.md). Add regressions for the actual discovery/admission path, not only a parser. State which checks ran and what remains unverified. Emulator success does not establish physical-device compatibility.

Keep pull requests reviewable: explain the problem, resulting behavior, implementation, tests, risks and manual verification needed. Use concise Conventional Commit messages. Do not report failed or unfinished discovery work as successful.

## Protect private data

Use synthetic names and addresses. Do not commit household scans, SSIDs, personal screenshots, serials, credentials, private keys or captures. Review staged files and run `python3 scripts/check-repository.py`; never force-add ignored artifacts. Emulator automation must reject phones. Physical checks need a specifically authorised target and must stop if Network Lens loses the foreground.

Report vulnerabilities through [Security reporting](SECURITY.md).

## Licensing

Contributions are licensed GPL-3.0-only, except clearly identified third-party material under compatible terms. Retain notices and provenance, and submit only material you have the right to contribute. No separate contributor agreement or copyright transfer is required. Do not incorporate Fing or Nmap code or databases.
