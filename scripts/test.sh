#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/setup-kotlin-toolchain.py
python3 scripts/generate-recog-models.py --check
python3 scripts/generate-apple-models.py --check
python3 scripts/test-apple-models.py
python3 scripts/generate-android-models.py --check
python3 scripts/generate-product-models.py --check
python3 scripts/generate-android-hardware.py --check
python3 scripts/generate-discovery-matchers.py --check
python3 scripts/update-matter-models.py --check
python3 scripts/check-recognition-coverage.py
python3 scripts/test-recognition-baseline.py
python3 scripts/test-phone-performance.py
python3 scripts/test-public-web-trial.py
mkdir -p .tools/tests
find .tools/tests -type f -delete
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
KOTLIN_DIR="$PWD/.tools/kotlin-0300/kotlinc"
STDLIB="$KOTLIN_DIR/lib/kotlin-stdlib.jar"
python3 - <<'PY'
from pathlib import Path
core='DiagnosticEvidence TroubleshootingReport RecognitionReport NetworkEquipment EsclInfo Ipv6Admission RecognitionStatus SsdpDiscovery ScanPlan ScanResources MetadataPlans Ipv6Routes ServiceHandle JavaText IpRange DataGuard ServiceAdmission SafeText Address NetBios Ssdp UpnpDescription Grouping IdentityHints DiscoveryHealth ReverseDns Sadp MdnsPacket MdnsTraffic MdnsQuestionSuppression DescriptionQueue WsDiscovery IcmpEcho DiscoveryWork FairWorkQueue MetadataCandidates IdentityProbePlan EnvisalinkInfo CastInfo SamsungInfo LanIdentity TuyaAnnouncement LifxIdentity EasySmartDiscovery BoundedJson LocalMetadata IppInfo MetadataHttp MetadataLookups MdnsRetries MdnsTypePlan HaMatchers DiscoveryMatchers SsdpMatchers MatterInfo VendorIndex TcpCheck HomeKitInfo LocalServices MdnsAssembly NativeServiceEvidence Recog RecogModels AppleModels DeviceCatalog FireTvInfo SmartThingsInfo KiddeInfo GoogleCastModels DevicePresentation RecognitionCopy EapInfo OmadaDiscovery TpLinkDiscovery TpLinkModels PlayStationDiscovery ProductModelIndex HardwareClassification AppleDeviceInfo AndroidHardwareIndex AudioZones AudioZoneInfo BoundedXml AudioPlayers BroadAudioInfo AudioWire AppUpdates UpdateTransfer'.split()
base=Path('android/app/src/main/java/io/github/kazands/networklens')
if any(Path('tests').rglob('*.java')):
 raise SystemExit('Test sources must be Kotlin; remove stale Java tests before running.')
sources=[base/(name+'.kt') for name in core]+sorted(Path('tests').glob('*.kt'))
if not all(path.is_file() for path in sources):
 raise SystemExit('A required Kotlin source file is missing.')
Path('.tools/tests/kt-sources.txt').write_text(''.join(str(path)+'\n' for path in sources))
PY
"$KOTLIN_DIR/bin/kotlinc" -Xjdk-release=8 -jvm-target 1.8 -no-reflect -d .tools/tests @.tools/tests/kt-sources.txt
for suite in ReportRegressionTest TroubleshootingReportTest RecognitionReportTest RecognitionCoverageTest NetworkEquipmentTest ScanLifecycleTest RecognitionBenchmarkTest RecognitionPlanningTest RoutedIpv6Test ServiceHandleTest IpRangeTest SecurityTest DiscoveryProtocolTest GroupingTest IdentificationTest DiscoveryUpgradeTest WsDiscoveryTest ConfirmedFixesTest IcmpEchoTest ScanEngineTest ResolverTest MdnsRetriesTest VendorIndexTest TcpCheckTest HomeKitTest LocalServicesTest MdnsAssemblyTest MdnsQueryTest LocalMetadataTest NativeServiceEvidenceTest RecogTest DeviceCatalogTest GoogleCastModelsTest FireTvInfoTest SmartThingsInfoTest KiddeInfoTest OmadaDiscoveryTest TpLinkDiscoveryTest PlayStationDiscoveryTest ProductModelIndexTest HardwareClassificationTest AppleDeviceInfoTest EnvisalinkInfoTest AndroidRecognitionTest AudioZonesTest BroadAudioTest AppUpdatesTest DiscoveryMatchersTest MatterInfoTest LanIdentityTest LifxIdentityTest EasySmartDiscoveryTest; do
    "${JAVA_BIN}java" -cp ".tools/tests:$STDLIB" "$suite"
done
