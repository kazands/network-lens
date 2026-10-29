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
"${JAVA_BIN}javac" -source 8 -target 8 -bootclasspath "$PLATFORM:$TOOLS/core-lambda-stubs.jar" -cp ".tools/build/classes.jar:$STDLIB" -d "$TEST_DIR/classes" tests/android/SmokeInstrumentation.java
"${JAVA_BIN}jar" cf "$TEST_DIR/classes.jar" -C "$TEST_DIR/classes" .
"${JAVA_BIN}java" -cp .tools/kotlin-0300/r8.jar com.android.tools.r8.D8 --min-api 26 --lib "$PLATFORM" --classpath .tools/build/classes.jar --classpath "$STDLIB" --output "$TEST_DIR/dex" "$TEST_DIR/classes.jar"
cp "$TEST_DIR/resources.apk" "$TEST_DIR/unsigned.apk"
(cd "$TEST_DIR/dex" && zip -q "$TEST_DIR/unsigned.apk" classes.dex)
"$TOOLS/zipalign" -f -p 4 "$TEST_DIR/unsigned.apk" "$TEST_DIR/aligned.apk"
python3 scripts/prepare-signing.py --keytool "${JAVA_BIN}keytool"
"$TOOLS/apksigner" sign --ks .tools/netlens-development.jks --ks-key-alias netlens --ks-pass file:.tools/netlens-signing-password --out "$TEST_DIR/tests.apk" "$TEST_DIR/aligned.apk"
