# Amazon Fire TV discovery

See [current release verification](../VERIFICATION-0.3.26.md) for exact artifact checks and physical-test limits.

## What changes

Network Lens explicitly queries `_amzn-wplay._tcp.` alongside its other initial
Bonjour services. A current, same-peer WhisperPlay manager advertisement can
supply a useful name through TXT `n`. The internal `amzn.dmgr:…` identifier and
opaque Amazon hostnames remain available in technical observations but are not
used as friendly list titles. Generic `Android.local` names are also excluded
from friendly-name selection.

Without a reviewed type code or useful reported name/model, a qualifying advertisement gives the
fallback title **Amazon Fire TV**. This is a service-family interpretation, not
an exact hardware identification. Details explain that distinction. WhisperPlay
appears in Services available and uses the casting icon unless stronger physical
evidence establishes another category. An Amazon service can be provided by a
stick, box, television or other Fire TV product; it cannot establish that the
device is specifically a first-generation 4K Stick.

TXT `ad`, when well formed, is shown separately as an advertised Amazon device
type code. One reviewed exact code, `AKPGW064GI9HE`, maps offline to **Amazon Fire
TV Stick 4K**, with a streaming-device category/icon and a useful room name
underneath. The title intentionally leaves the generation unspecified. The code
is not put in Model, converted to a MAC, used for grouping, or looked up online.
Unknown Manufacturer, Model and MAC fields stay unknown. Unknown type codes
retain the service-family fallback. Contradictory reported manufacturers,
models or type codes suppress the product interpretation, including across
grouped addresses. Conflicts remain sticky within a scan.
Known Amazon hardware evidence also constrains the separate Apple recognition
path: an AirPlay or device-info Apple model cannot replace a conflicting Fire TV
identity or supply an incompatible physical category. The known evidence survives
later unknown/conflicting type codes, copies and grouped-address checks. Unknown
Amazon codes alone do not establish a physical hardware identity. Reported fields
remain unchanged.
Physical classification checks the combined address group as well as each
catalogue model. A conflicting Apple or Google model cannot restore a tablet or
speaker category through another address. Such conflicts show **Conflicting
reports** as the primary type and use a neutral icon; original observations and
service capabilities remain available in details.
Generic Alexa advertisements do not receive the Fire TV family interpretation.
Native/cached records still provide candidates only; current-wire admission,
scope, cancellation, scan isolation and source labels are unchanged.

SSDP sends two `ssdp:all` searches and two targeted DIAL searches, staggered
inside the existing five-second window. Its packet cap, deduplication and scan
deadline remain unchanged. The description reader additionally accepts the
documented `/upnp/dev/<UUID>/desc` path when supplied in the same peer's LOCATION.
It preserves numeric same-peer addressing, credential/query/fragment restrictions,
GET-only requests, response limits, timeouts and no redirects. It does not guess
the UUID, fetch application state, launch apps, pair, or control playback.

## Research and limits

Research checked on 2026-11-18. New protocol code is independently implemented.
One public device-type fact is adapted from the MIT-licensed ioBroker.alexa2
table, with bundled license and [pinned provenance](../../android/fire-tv-provenance.json).
No third-party SDK, account access or cloud-recognition code is included.

| Finding | Evidence and confidence | Decision |
| --- | --- | --- |
| Fire TV uses WhisperPlay and supports targeted DIAL discovery; its description URL can end in `/upnp/dev/<UUID>/desc`. | [Amazon DIAL documentation](https://developer.amazon.com/docs/fire-tv/dial-integration.html), documented behavior, directly supports, accessible. The old URL guard was confirmed to reject this form. | Add the target and narrowly admit the documented path. This compatibility defect is confirmed in code; receiving such a URL from the user's device has not been verified. |
| Fling discovery uses `_amzn-wplay._tcp`. | [Amazon iOS Fling guide](https://www.developer.amazon.com/ja/docs/fling/integrating-amazon-fling-into-your-ios-app.html), documented behavior, supports service discovery, accessible localized official page; the English URL was unavailable. | Query it explicitly and expose the capability. |
| Manager advertisements use an internal instance identifier, and TXT `n` can contain the friendly name. | [Original protocol observation](https://github.com/Sor3nt/Amazon-Fire-TV-Stream/issues/1), observed behavior reported by an implementer, supports the field meaning and dynamic port, accessible. Amazon does not publish a full TXT schema here. | Parse only bounded, valid text; keep the identifier as technical evidence. Do not infer a retail model from the friendly name. |
| `AKPGW064GI9HE` identifies a Fire TV Stick 4K. | [ioBroker.alexa2 pinned table](https://github.com/Apollon77/ioBroker.alexa2/blob/6471cf116653e50b6cbc6c4dd54c2d52bfea4190/main.js), implementation evidence, supports product family, accessible and MIT-licensed. The independently inspected Alexa Media Player table agrees on 4K Stick but uses a different generation naming scheme. A fresh authorized LAN advertisement supplied this exact public type code. | Add only the verified code mapping. Normalize the retail name; do not repeat inconsistent generation labels or generalize unknown codes. |
| Build model `AFTMM` is not globally unique to one retail product. | [Amazon device identification table](https://developer.amazon.com/docs/device-specs/identify-fire-tv-devices.html), documented behavior, directly supports ambiguity, accessible. It includes the 2018 4K Stick and Fire TV soundbars. | Do not blindly map a service, port or ambiguous code to “Fire TV Stick 4K (1st generation)”. |
| A read-only `getFullDeviceInfo` method exists in WhisperPlay. | [Bitdefender primary research](https://www.bitdefender.com/files/News/CaseStudies/study/430/Bitdefender-PR-Whitepaper-AMZFr-creat6696-en-EN.pdf), observed protocol analysis, supports existence but does not provide a complete identity schema or establish exact model availability on current firmware. | Not implemented from an incomplete schema. No service registration, authentication bypass or remote-control requests are part of discovery. |

An initial bounded follow-up returned no complete record. A subsequent check
with split-record follow-up queries received a current WhisperPlay manager
advertisement, a nonempty friendly-name field and the public type code above.
Its advertised service port differed from the earlier scan: the port is dynamic
and is not a recognition key. No household identifiers, friendly-name values or
packet contents are saved in this document or the regression fixtures.
The new production parser was then run against another fresh reply: it accepted
the friendly name and returned Amazon Fire TV Stick 4K from the current `ad`
field. The diagnostic printed only the recognized product and a boolean for
friendly-name acceptance, not the private name or identifiers.
Generation-specific identification and an installed-app scan on a physical
phone remain unverified. A DIAL description response from this target has not
been obtained; the URL change is backed by official documentation and tests.

## Regression coverage

`FireTvInfoTest` covers explicit query priority, friendly names, malformed TXT,
conflicting keys, misleading names, other Amazon services, port bounds, capability
separation, the exact public type mapping, contradictory/grouped evidence,
snapshot isolation and guarded description URLs. `ScanLifecycleTest` exercises a receiver
that answers only the DIAL target, retries, deduplication, scope and deadlines.

The emulator-only `fire-tv` mode, also included in `device-identities`, exercises
current advertisement application, list labels/icons, raw-field preservation,
source labels, dual-stack grouping, cached-native exclusion, Stop and fresh scans.
It also covers conflicting Amazon/Apple observations in both arrival orders,
independent snapshots, grouped endpoints and late conflicting type codes.
These fixtures do not query a LAN or operate physical devices.

## Verification

The regression coverage includes the product-name and physical-classification
conflicts identified in independent review. Known Amazon hardware evidence now
constrains both recognition engines and the combined address group's category.
Rejected model interpretations cannot restore a definite primary type or icon;
raw observations remain available for inspection.

Verified for the current source on 2026-11-18:

- The new catalogue regression reproduced the speaker-category failure before
  the fix. Both review reproductions now suppress the definite category and
  report a conflict in either arrival order.
- All 44 Kotlin host suites, seven generated-data checks and seven offline
  Python tests passed. The Amazon suite contains 86 checks. The restricted host
  run could not open its synthetic loopback server; the rerun with local test
  connections enabled passed.
- Direct development APK, permission/security audits and instrumentation builds
  passed, as did `git diff --check`.
- API 37 emulator: `fire-tv` passed 72 checks and `device-identities` passed 926,
  for 998 total, with installed APK hashes verified. The isolated emulator was
  stopped afterward.

The earlier authorized LAN/parser check is described under Research and limits;
these candidate checks used synthetic fixtures and did not install the APK on
a phone. A subsequent independent Deep kaz-code-review approved the complete change
with no substantive findings. It ran 13 focused host suites (2,937 checks);
78 additional ordering, copying, grouping and valid-identity checks passed.
The candidate above retained 0.3.21 during development. Exact versioned release
builds and checks are recorded separately in [0.3.22 verification](../archive/VERIFICATION-0.3.22.md).
