# Understanding device recognition

Current scans discover IPv4 devices on the selected Wi-Fi. IPv6-only devices and routed Thread endpoints are outside scope. See [IPv4 scope and limitations](reference/IPV4-SCOPE.md).

Network Lens separates four questions: did a device respond, what information did it share, what product does that information identify, and what services does it offer?

## Names and models

A product name such as **Sony PlayStation 5** comes from supported identity evidence and the local catalogue. A room name is a device's friendly name and remains useful alongside the product. Original fields and sources remain available in technical details.

A catalogue contains possible names; it cannot identify a device that supplies no matching information. The bundled catalogue covers 86,540 product/family titles across 5,263 brands. That is a naming resource, not a claim that every listed product can be discovered or identified on a network.

Some reported model codes are shared by several generations. Network Lens keeps the broader family name when the code cannot distinguish them. Contradictory reports can suppress recognition instead of choosing an unsupported answer.

## Router-reported names

For devices that have already responded, Network Lens can ask a configured on-link private DNS server for a name and check that it resolves back to the same address. The result is diagnostic information only: router records can remain stale even when the two lookups agree.

Router names appear under **Show technical details** and in troubleshooting reports, with masking by default. They never supply a product/family suggestion, list title, reported manufacturer/model, primary type, presence or grouping. The earlier Hydrawise and B-hyve name-based suggestions have been removed. See [local network name diagnostics](reference/IRRIGATION.md).

## Device type and services

A device may offer several services on different ports. Those services do not necessarily describe its physical purpose. An identified thermostat can offer audio playback; a computer can share a scanner; a speaker group can be a virtual playback target.

Network Lens keeps service capabilities separate from physical identity. Scanner names and models stay with the scanning service. Audio groups and zones do not automatically become separate physical devices. Missing manufacturer, model and MAC values stay unknown, and an IEEE network-interface supplier is not treated as a reported manufacturer.

For unresolved current responders, bounded public web, certificate and SSH checks
can provide additional service clues. Corroborated Home Assistant, Pi-hole,
Tasmota and OctoPrint pages can identify software services without identifying
their host hardware. A direct local hostname response can supply a reported
name. Selected clues and lookup timing are available in technical details; a
generic title, server label or certificate name cannot establish a hardware
model. See [public identity readers](reference/PUBLIC-IDENTITY.md).

## Coverage at a glance

| Device family | Available evidence | Main limit |
| --- | --- | --- |
| Apple computers, phones and tablets | Bonjour model fields and a read-only device-service lookup | Private addresses and a reachable port alone cannot establish a model |
| Google and Android devices | Cast, Android TV and supported model fields | Shared or absent model strings may identify only a family |
| TVs and streaming devices | Local service descriptions; reported ARRIS/FibeTV pairs; supported Samsung, Google and Fire TV fields | Availability differs by firmware and power state |
| Audio systems | VSSL, Sonos, HEOS, MusicCast, BluOS and AirPlay/Cast relationships | A zone or group is not necessarily separate hardware |
| Smart-home devices | Supported mDNS, SSDP, HomeKit, Matter and vendor announcements | Hub-attached Zigbee/Z-Wave devices and paired-only information may not be exposed over IP |
| Kidde smart detectors | Matching current Kidde WD-family discovery fields | Family identification only; exact model, smoke/CO capabilities and HomeKit compatibility are not established |
| TP-Link equipment and smart devices | Kasa, Omada/EAP and Easy Smart discovery | Models and firmware expose different local responses |
| Other network equipment | Public Ubiquiti discovery and passive MikroTik announcements | The device must expose supported discovery on the selected network |
| Printers and scanners | IPP and advertised eSCL capabilities | An attached peripheral must not replace the computer hosting it |
| Consoles | Supported PS5 replies | No login, control commands or inferred generation |

All 174 bundled Home Assistant integration domains have an explicit interpretation policy. These labels improve family-level results; they do not guarantee exact models. See the [generated coverage inventory](reference/RECOGNITION-COVERAGE.md) and [protocol references](reference/README.md) for precise rules.

Network Lens no longer offers the dedicated Envisalink TPI check because even a receive-only connection can compete with an alarm integration. See [the Envisalink reference](reference/ENVISALINK.md).

## Why devices can remain unnamed

A device can answer a presence check without sharing a name, model or manufacturer. Other devices sleep, block discovery, expose only cloud or authenticated interfaces, or sit behind network isolation. An unknown result is not evidence of an intruder.

Wake the device, run a fresh scan and inspect its technical details. If a model is present but no product is recognised, the catalogue or interpretation rules may need improvement. If no identity information arrives, adding product names alone will not solve it.

[Report a recognition problem](TROUBLESHOOTING.md#report-a-recognition-problem) · [Documentation home](README.md)
