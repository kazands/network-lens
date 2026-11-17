# Recognition planning follow-up

Historical implementation/candidate evidence for functionality included in
**0.3.19**. Candidate version labels, hashes and publication status below describe
those earlier checks. See [final release verification](VERIFICATION-0.3.19.md).

This unpublished change improves the opportunity to obtain current identity.
The catalogue, fresh-response requirement and RAM-only lifetime remain the same.

## Independent identity opportunities

After general port checks stop at their first response, the detail worker separately
checks any untested identity ports: HTTP (80, for public EAP metadata), Apple device
service (62078), and Samsung identity (8001). A web, HTTPS or SSH response cannot
suppress these opportunities. Ports already checked are not repeated. Each check
still uses the selected Wi-Fi, current endpoint, cancellation and existing timeout
bounds; an open port never supplies a make/model by itself.

## Alternate metadata targets

An endpoint/protocol can retain two distinct validated requests. A current
advertisement can supply another port/path after the first request fails. There
is one worker per plan, and later advertisements do not extend its original
two-second lookup window or the overall scan deadline. Each target permits at
most one transient transport retry, so the bound is four transport attempts.
Alternatives must keep the original protocol and transport security; IPPS can
never fall back to plaintext IPP. Successful identity closes the plan; repeated/changed advertisements cannot
continually grow it. Plans and paths are scan-local RAM and are not copied into
exported device diagnostics or persisted.

Each alternate gets its own lookup outcome. Final source failures are recorded
from the final sealed endpoint outcomes, so a recovered first-target failure does
not leave the source failed. The failed-target counter remains visible. An
unrecovered final failure still makes source coverage incomplete.

No neighbour's metadata is reused as a fallback. IPv4 and IPv6 endpoints continue
to require their own current evidence; this change does not assume they offer
identical services or weaken grouping rules.

## Fair bounded scheduling

Cast and other metadata queues rotate between queued endpoint keys. Each endpoint
can hold at most eight waiting tasks in either queue, inside the existing global
32-task queue bound and two-worker pool. An endpoint cannot fill the whole queue
with its multiple protocols/players. Running operations are not preempted;
per-request deadlines still bound slow peers. Admission failures remain reported.
Other discovery lanes keep their existing independent capacities.

The service-instance limits remain 256 total and 32 per type. The 512-type browse
limit is a different bound. This work does not claim that arbitrary large networks
will fit inside all budgets.

## Accuracy measurement

`RecognitionBenchmarkTest` is a labelled, synthetic starter corpus. It separately
counts expected identifications, correct titles, missed titles, false-positive
products and incorrect primary categories. It includes multifunction thermostats,
a television with audio capability, regional aliases, a console, a streamer,
a recorder, an amplifier, missing/conflicting manufacturers and near-match models.
It uses public model spellings and synthetic endpoints; no household capture or
personal device identifier belongs in fixtures.

The resulting scores apply only to this corpus. They are not measured household
recognition accuracy, regional coverage, firmware coverage or a comparison with
another scanner. Future physical checks should record device make/model/firmware,
protocol support, awake/asleep state, IPv4/IPv6 behaviour, expected identity and
observed outcome without committing addresses, personal names or raw captures.

Broad catalogue category expansion and secure/vendor-specific readers still need
independent provenance and compatibility evidence. Unknown types stay unknown;
these scheduling fixes do not create reliable categories for arbitrary catalogue
entries or bypass TLS authentication.

## Checks

`RecognitionPlanningTest` checks independent-port policy, bounded target admission,
shared deadlines, late alternatives, sealed diagnostics, fair queues and cleanup.
The Android local-metadata mode additionally drives a synthetic first-target HTTP
failure followed by a successful alternate through Discovery, asserting current
identity, provenance, one deadline, final recovery status and refusal after Stop.
The injected reader has no Android Network and opens no LAN sockets.

All 41 host suites passed. The labelled starter corpus reports 14 cases, eight
expected identifications, eight correct titles, zero missed titles, zero false
positives and zero wrong primary categories. The planning/fairness suite passed
41 checks initially; the final transport-security follow-up passed 44 checks,
including rejection of security/protocol changes between targets. A fresh independent Deep review approved the corrected implementation;
it independently ran both new host suites. Review identified and verified the
fix for a late queued lookup leaving an incomplete endpoint but an OK source.
Final Android results are recorded below when complete.

## Candidate verification before the final security guard

The final direct application and instrumentation builds passed, including packaged
permission/security audits. Installed hashes are verified by the emulator runner.

- Application SHA-256: `70167dae56ba69be2dae8dfd5032a89735d695b7ada64b426804f9ca5586d165`.
- Instrumentation SHA-256: `df6c91f8484484a8e2a631a6568f4fa6908d2ea8e57954dbc8a7bdb05fc9709c`.
- Android 8 / API 26: all five selected modes passed, **244 checks**: local-metadata,
  scan-efficiency, failure-reporting, scan-details and lifecycle.

The first selected Android 17 run failed while an Android System UI ANR dialog
covered the application (`Application Not Responding: com.android.systemui`).
Local-metadata, scan-efficiency and service-retries passed before the affected UI
checks. The original failed result is retained locally. A full selected rerun was
started after rebooting the emulator; its final result is recorded below.

This candidate has not been installed or scanned on a physical phone. No physical
compatibility or household accuracy claim follows from the synthetic corpus.
Catalogue category expansion, secure protocol coverage and comprehensive
IPv4/IPv6 hardware compatibility remain documented follow-up work.

The independent reviewer also approved the targeted no-downgrade guard. Its three
new regressions reject both security-flag directions and protocol mismatch before
queue mutation. The final application rebuild includes this additional guard;
its artifact hashes and focused Android results supersede the earlier candidate
identity above when recorded below.

After rebooting the Android 17 emulator, all eight selected modes passed,
**1,172 checks**: local-metadata, scan-efficiency, service-retries, failure-reporting,
scan-details, device-identities, lifecycle and permission. No failed mode was
retried inside that completed rerun. The earlier System UI ANR run is not called
a pass. The subsequent guard-only build has a separate focused run below.

## Final guarded candidate

The direct build, packaged permission/security audits and instrumentation build
passed again after the no-downgrade guard. The installed application/test hashes
were verified. Android 17 passed all three focused modes, **134 checks**:
local-metadata (60), failure-reporting (53) and permission (21). These include the
Discovery fallback and expired-start regressions. The full preceding Android 8/17
matrices above apply to the prior candidate before this isolated guard change.

- Final application SHA-256: `9a7531fe8e86e176d2cc9ea39127710fbe2239136e0579bc1b0c8ec58785e6d2`.
- Final instrumentation SHA-256: `0f228a837d75954310a579b99f7bda6c52f6689366abfe68118b9f581ca26618`.

Working-tree whitespace and the repository credential/prohibited-path guard
passed. No PR, merge, release publication or physical-phone installation occurred
in this follow-up. The candidate remains locally labelled 0.3.18 and requires a
new release version before publication.
