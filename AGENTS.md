# Network Lens project guidance

Network Lens is a focused Kotlin Android Wi-Fi device finder. Keep its product scope simple: user-started foreground scans, one searchable list, detailed source-labelled observations and clear scan status.

- Every scan starts fresh. Keep scan results, preferences and appearance in memory only; no saved history, persistent Wi-Fi fingerprint, analytics or cloud recognition.
- A current response is required for presence. Treat cached/native advertisements as candidates. Do not add router PTR/DHCP names to scan identity or reuse an IP's prior occupant's details.
- Bind all discovery to the selected Wi-Fi and eligible scope. Preserve bounded work, deadlines, retries, cancellation, error reporting and resource cleanup.
- Missing MAC/model/manufacturer values remain unknown. HomeKit pairing IDs are not hardware MAC addresses. Keep IEEE interface vendor separate from reported manufacturer.
- Preserve light/dark appearance, scalable text, Lucide device icons, shared Network Lens branding, IPv6 below IPv4 and Back/list-position behavior.
- Tests use synthetic identifiers. Do not save or commit household scans, device serials, SSIDs, credentials, private keys or personal screenshots. Local caches/evidence/artifacts are ignored. Never force-add them.
- Emulator automation must reject physical devices. Physical-device checks require a specifically authorized target and must stop if the app loses the foreground. Do not operate unrelated connected devices.
- Use the direct build and relevant checks in docs/BUILDING.md. Report exactly what passed and what remains unverified. Do not call a Partial scan complete or claim that reporting fixes resolve a discovery issue.
- Original code is GPL-3.0-only. Retain all upstream notices/provenance. Do not incorporate Fing/Nmap code or databases merely because research describes their behavior.
