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
core='Ipv6Routes ServiceHandle JavaText IpRange DataGuard ServiceAdmission SafeText Address NetBios Ssdp UpnpDescription Grouping IdentityHints DiscoveryHealth ReverseDns Sadp MdnsPacket DescriptionQueue WsDiscovery IcmpEcho DiscoveryWork CastInfo MdnsRetries VendorIndex TcpCheck HomeKitInfo LocalServices MdnsAssembly'.split()
base=Path('android/app/src/main/java/dev/netlens')
for ext in ['kt','java']:
 Path('.tools/tests/'+ext+'-sources.txt').write_text(''.join(str(base/(n+'.'+ext))+'\n' for n in core if (base/(n+'.'+ext)).exists()))
PY
"$KOTLIN_DIR/bin/kotlinc" -Xjdk-release=8 -jvm-target 1.8 -no-reflect -d .tools/tests @.tools/tests/kt-sources.txt @.tools/tests/java-sources.txt
"${JAVA_BIN}javac" -encoding UTF-8 -source 8 -target 8 -cp ".tools/tests:$STDLIB" -d .tools/tests @.tools/tests/java-sources.txt tests/*.java
for suite in RoutedIpv6Test ServiceHandleTest IpRangeTest SecurityTest DiscoveryProtocolTest GroupingTest IdentificationTest DiscoveryUpgradeTest WsDiscoveryTest ConfirmedFixesTest IcmpEchoTest ScanEngineTest ResolverTest MdnsRetriesTest VendorIndexTest TcpCheckTest HomeKitTest LocalServicesTest MdnsAssemblyTest; do
    "${JAVA_BIN}java" -cp ".tools/tests:$STDLIB" "$suite"
done
