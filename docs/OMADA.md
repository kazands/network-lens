# Omada and EAP network equipment discovery

Included in Network Lens 0.3.22. See [release verification](VERIFICATION-0.3.22.md)
for exact checks and remaining physical compatibility limits.

## What is supported

Network Lens listens for current Omada discovery announcements on UDP 29810 during
an explicitly started scan. Supported plaintext ECSP version 1.x.x/2.x.x messages
report a model and a device family: access point, Ethernet switch or gateway.
The list uses a protocol-qualified title such as **TP-Link EAP245** or
**TP-Link SG2008**, with a Wi-Fi access point, network switch or router category.
A gateway announcement does not establish firewall capabilities.

A separate, bounded public web check supports EAP firmware that exposes its model
on the login screen. A currently responding endpoint must already have TCP 80
confirmed. Network Lens first reads `/`, requires the TP-Link HTTPD server header
and the reviewed EAP page markers, then sends exactly `operation=read` to the same
peer's `/data/login.json`. A successful response must contain numeric `error: 0`
and a valid `dutMode` EAP model. Template text and HTML comments never supply a
model. This path was checked with actual EAP245 and EAP265 HD responses.

These are independent identity sources. Receiving an Omada announcement is not
required for the public EAP check. Neither mechanism is universal TP-Link support:
non-Omada Ethernet switches, other firmware interfaces, HTTPS-only endpoints and
authentication-only identity pages may remain unidentified. An authorized Ethernet
switch check returned an authentication gate without a model; no compatibility is
claimed for it. Kasa/Tapo plugs and light switches use the separate
[TP-Link discovery methods](TP-LINK.md).

## Evidence and display

Model, name, firmware, hardware version and MAC are retained only when explicitly
reported by a supported current announcement. The packet sender supplies the
address; embedded IPs, controller settings, serials and credentials are ignored.
The protocol qualifies the TP-Link product title but does not invent a reported
manufacturer. IEEE interface vendor remains separate. The HTTP path supplies only
a model and the corroborated EAP hardware family; missing MAC and manufacturer
remain unknown. Conflicting models or hardware families remain explicit and
suppress an otherwise misleading product/category match.

Physical hardware identity takes precedence over playback/service capabilities.
Ethernet switches have a network-device icon, not a smart light-switch icon.
Raw observations and source labels remain in Details. Every scan starts fresh.

## Safety, scope and limits

- The UDP path only receives. It sends no controller reply, adoption, provisioning,
  discovery redirection, management or configuration command.
- The socket is bound to the selected Android Wi-Fi Network; accepted senders must
  be IPv4 peers in the eligible scan scope. A self-only scan skips the listener.
  An occupied UDP port is reported rather than shared with a controller.
- Listening lasts at most ten seconds within the existing scan deadline, with
  receive waits at most 250 ms. Limits are 512 packets, 128 devices, eight distinct
  reports per address and 2,004 bytes per datagram, including its four-byte length.
  Malformed, oversized, compressed and unsupported messages are rejected.
- Public HTTP checks admit at most 32 current endpoints per scan, at most two
  requests each, with a two-second total deadline. Connect/read timeouts are
  400/600 ms. HTTP framing/body limits are shared with the bounded UPnP reader
  (64 KiB body); accepted identity JSON is additionally limited to 4 KiB.
- HTTP is same-peer, fixed-port and fixed-path. There is no login, password guessing,
  cookie reuse, redirect following, HTTPS downgrade or settings change. An unrelated
  web service receives only the initial public page request.
- Stop, network changes and scan completion close registered sockets and reject
  late identity updates. Outcomes, failures and safety limits remain visible in
  scan details. No response is not proof of absence or unsupported hardware.

A passive listener can miss equipment that does not announce during its window,
including equipment separated by broadcast filtering. The authorized LAN check
observed no announcements in a longer 35-second listen; that does not verify
on-device Omada discovery. Actual Android Wi-Fi delivery and switch/gateway
announcement compatibility still need physical testing. Synthetic protocol tests
cannot establish real-device coverage.

## Protocol provenance

The implementation is original GPL-3.0-only code. No vendor firmware, Fing/Nmap
implementation or database was incorporated. Public code was consulted to establish
message framing/fields, not copied into Network Lens:

- [TP-Link's Omada port documentation](https://support.omadanetworks.com/us/document/13090/)
  describes UDP 29810 and separates discovery from management traffic.
- [TP-Link EAP user guide](https://static.tp-link.com/1910012276_EAPXXX_UG.pdf)
  documents the public login interface. The exact read-only exchange was established
  from the interface served by authorized physical EAPs, then exercised by the
  production Kotlin transport. No household captures are committed.
- [Open Omada device-agent wire format](https://github.com/adryanalencar/open-omada-device-agent/blob/def9cadf44b60e3aea6afb4a9a3d2c99812288db/docs/ecsp-wire-format.md)
  and its protocol overview establish length-prefixed JSON and device-to-controller
  type 1 announcements.
- [Awto Omada protocol notes](https://github.com/awto-au/awto-omada/blob/f0cb12f674f5960e46e0512ab416850bc791b021/docs/discovery.md)
  corroborate device families and the discovery envelope.
- [Omada discovery proxy](https://github.com/chrstnwhlrt/omada-discovery-proxy/tree/390fb4817341444344230d3603cd5edb8c799489)
  demonstrates why replying as a controller is outside this application's scope.

## Regression coverage

`OmadaDiscoveryTest` covers framing, typed fields, malformed/duplicate JSON,
unsupported traffic, spoofed embedded addresses, scope, deadlines, cancellation,
packet/device limits, conflicting reports, catalogue copies/grouping, public EAP
page gating and the actual HTTP sequence using synthetic loopback fixtures.

The `device-identities` Android mode includes synthetic access point/switch
callbacks, source labels, presence/scope guards, classification and icon priority,
replaced endpoints, Stop, fresh-scan isolation, plus native display checks in both
appearances and text sizes. See [BUILDING.md](BUILDING.md) for commands and
[STATUS.md](STATUS.md) for the checks actually completed.

The candidate verification history above predates release versioning. See
[current release verification](VERIFICATION-0.3.22.md) for the final artifact and checks.
