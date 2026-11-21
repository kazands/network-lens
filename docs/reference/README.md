# Technical reference

These pages describe implementation contracts, exact protocol coverage, provenance and limits. For normal app use, start with [the user guides](../README.md).

## Recognition and discovery

### Identity rules and catalogues

- [Canonical device identities](DEVICE-IDENTITIES.md)
- [Recognition evidence and compatibility](RECOGNITION-EVIDENCE.md)
- [Offline model recognition](RECOG.md)
- [Recognition family coverage](RECOGNITION-COVERAGE.md)
- [Broader local recognition](DISCOVERY-COVERAGE.md)
- [Recognition coverage expansion](RECOGNITION-EXPANSION.md)

### Device families and metadata

- [Apple recognition sources](APPLE-RECOGNITION.md)
- [Android and Cast recognition](ANDROID-RECOGNITION.md)
- [Amazon Fire TV discovery](FIRE-TV-RECOGNITION.md)
- [SmartThings hub recognition](SMARTTHINGS.md)
- [Envisalink recognition](ENVISALINK.md)
- [Kidde smart detectors](KIDDE.md) — current WD-family fingerprint, evidence limits and tests.
- [Local network name diagnostics](IRRIGATION.md) — router names kept separate from device identity.
- [TP-Link plug and switch discovery](TP-LINK.md)
- [Omada and EAP network equipment discovery](OMADA.md)
- [Public equipment identity](NETWORK-EQUIPMENT.md)
- [Audio zones and physical hardware](AUDIO-ZONES.md)
- [Local device metadata](LOCAL-METADATA.md)
- [Public identity readers](PUBLIC-IDENTITY.md)

### Architecture, validation and distribution

- [Scan ownership and evidence flow](SCAN-ARCHITECTURE.md)
- [Home-user interface wording](INTERFACE.md)
- [Design system](DESIGN-SYSTEM.md)
- [Compose migration verification](../COMPOSE-VERIFICATION.md)
- [Recognition and performance baselines](RECOGNITION-BASELINE.md)
- [In-app updates](APP-UPDATES.md)
- [Application identity and updates](APPLICATION-IDENTITY.md)
- [Release signing](RELEASE-SIGNING.md)

Historical release-specific results are in [the archive](../archive/README.md). Exact current checks are in [0.3.36 verification](../VERIFICATION-0.3.36.md).
