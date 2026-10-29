#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."
SDK_DIR="${ANDROID_SDK_ROOT:-$PWD/.tools/sdk}"
TOOLS="$SDK_DIR/android-16"
if [[ ! -x "$TOOLS/aapt2" ]]; then TOOLS="$SDK_DIR/build-tools/36.0.0"; fi
PLATFORM="$SDK_DIR/android-37.0/android.jar"
if [[ ! -f "$PLATFORM" ]]; then PLATFORM="$SDK_DIR/platforms/android-37.0/android.jar"; fi
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
TEST_DIR="$PWD/.tools/android-tests"
mkdir -p "$TEST_DIR/classes" "$TEST_DIR/dex"
find "$TEST_DIR/classes" "$TEST_DIR/dex" -type f -delete
python3 scripts/setup-kotlin-toolchain.py
STDLIB="$PWD/.tools/kotlin-0300/kotlinc/lib/kotlin-stdlib.jar"
"$TOOLS/aapt2" link -o "$TEST_DIR/resources.apk" --manifest tests/android/AndroidManifest.xml -I "$PLATFORM"
python3 - <<'PY'
from pathlib import Path
if any(Path('tests').rglob('*.java')):
    raise SystemExit('Test sources must be Kotlin; remove stale Java tests before building.')
sources = sorted(Path('tests/android').glob('*.kt'))
if not sources:
    raise SystemExit('Android Kotlin instrumentation sources are missing.')
Path('.tools/android-tests/kt-sources.txt').write_text(''.join(str(path) + '\n' for path in sources))
PY
"$PWD/.tools/kotlin-0300/kotlinc/bin/kotlinc" -no-jdk -no-reflect -jvm-target 1.8 \
    -Xfriend-paths="$PWD/.tools/build/classes.jar" \
    -classpath ".tools/build/classes.jar:$PLATFORM:$STDLIB" \
    -d "$TEST_DIR/classes" @"$TEST_DIR/kt-sources.txt"
"${JAVA_BIN}jar" cf "$TEST_DIR/classes.jar" -C "$TEST_DIR/classes" .
"${JAVA_BIN}java" -cp .tools/kotlin-0300/r8.jar com.android.tools.r8.D8 --min-api 26 --lib "$PLATFORM" --classpath .tools/build/classes.jar --classpath "$STDLIB" --output "$TEST_DIR/dex" "$TEST_DIR/classes.jar"
cp "$TEST_DIR/resources.apk" "$TEST_DIR/unsigned.apk"
(cd "$TEST_DIR/dex" && zip -q "$TEST_DIR/unsigned.apk" classes.dex)
"$TOOLS/zipalign" -f -p 4 "$TEST_DIR/unsigned.apk" "$TEST_DIR/aligned.apk"
python3 scripts/prepare-signing.py --keytool "${JAVA_BIN}keytool"
"$TOOLS/apksigner" sign --ks .tools/netlens-development.jks --ks-key-alias netlens --ks-pass file:.tools/netlens-signing-password --out "$TEST_DIR/tests.apk" "$TEST_DIR/aligned.apk"
