#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/setup-kotlin-toolchain.py
mkdir -p .tools/tests
find .tools/tests -type f -delete
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
KOTLIN_DIR="$PWD/.tools/kotlin-0300/kotlinc"
STDLIB="$KOTLIN_DIR/lib/kotlin-stdlib.jar"
python3 - <<'PY'
from pathlib import Path
core='Ipv6Routes ServiceHandle JavaText IpRange DataGuard ServiceAdmission SafeText Address NetBios Ssdp UpnpDescription Grouping IdentityHints DiscoveryHealth ReverseDns Sadp MdnsPacket MdnsTraffic DescriptionQueue WsDiscovery IcmpEcho DiscoveryWork CastInfo BoundedJson LocalMetadata IppInfo MetadataHttp MdnsRetries VendorIndex TcpCheck HomeKitInfo LocalServices MdnsAssembly'.split()
base=Path('android/app/src/main/java/io/github/kazands/networklens')
if any(Path('tests').rglob('*.java')):
 raise SystemExit('Test sources must be Kotlin; remove stale Java tests before running.')
sources=[base/(name+'.kt') for name in core]+sorted(Path('tests').glob('*.kt'))
if not all(path.is_file() for path in sources):
 raise SystemExit('A required Kotlin source file is missing.')
Path('.tools/tests/kt-sources.txt').write_text(''.join(str(path)+'\n' for path in sources))
PY
"$KOTLIN_DIR/bin/kotlinc" -Xjdk-release=8 -jvm-target 1.8 -no-reflect -d .tools/tests @.tools/tests/kt-sources.txt
for suite in RoutedIpv6Test ServiceHandleTest IpRangeTest SecurityTest DiscoveryProtocolTest GroupingTest IdentificationTest DiscoveryUpgradeTest WsDiscoveryTest ConfirmedFixesTest IcmpEchoTest ScanEngineTest ResolverTest MdnsRetriesTest VendorIndexTest TcpCheckTest HomeKitTest LocalServicesTest MdnsAssemblyTest LocalMetadataTest; do
    "${JAVA_BIN}java" -cp ".tools/tests:$STDLIB" "$suite"
done
