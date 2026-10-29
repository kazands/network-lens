#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."
SDK_DIR="${ANDROID_SDK_ROOT:-$PWD/.tools/sdk}"
BUILD_TOOLS="${NETLENS_BUILD_TOOLS:-$SDK_DIR/android-16}"
if [[ ! -x "$BUILD_TOOLS/aapt2" ]]; then BUILD_TOOLS="$SDK_DIR/build-tools/36.0.0"; fi
PLATFORM="$SDK_DIR/android-37.0/android.jar"
if [[ ! -f "$PLATFORM" ]]; then PLATFORM="$SDK_DIR/platforms/android-37.0/android.jar"; fi
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
if ! command -v "${JAVA_BIN}javac" >/dev/null; then echo 'Set JAVA_HOME to a full JDK 17 or later.' >&2; exit 1; fi
if [[ ! -f "$PLATFORM" || ! -x "$BUILD_TOOLS/aapt2" ]]; then echo 'Android SDK platform 37.0 and build-tools 36.0.0 are required. See README.md.' >&2; exit 1; fi
BUILD_DIR="$PWD/.tools/build"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/generated" "$BUILD_DIR/dex" artifacts
# Remove only this script's generated output so removed source files cannot survive a rebuild.
find "$BUILD_DIR/classes" "$BUILD_DIR/generated" "$BUILD_DIR/dex" -type f -delete
"$BUILD_TOOLS/aapt2" compile --dir android/app/src/main/res -o "$BUILD_DIR/resources.zip"
sed 's/<manifest /<manifest package="dev.netlens" /' android/app/src/main/AndroidManifest.xml > "$BUILD_DIR/AndroidManifest.xml"
"$BUILD_TOOLS/aapt2" link -o "$BUILD_DIR/resources.apk" --manifest "$BUILD_DIR/AndroidManifest.xml" -I "$PLATFORM" --java "$BUILD_DIR/generated" "$BUILD_DIR/resources.zip"
python3 scripts/setup-kotlin-toolchain.py
KOTLIN_DIR="$PWD/.tools/kotlin-0300/kotlinc"
STDLIB="$KOTLIN_DIR/lib/kotlin-stdlib.jar"
find "$BUILD_DIR/generated" -name '*.java' > "$BUILD_DIR/resources-sources.txt"
"${JAVA_BIN}javac" -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PLATFORM" -d "$BUILD_DIR/classes" @"$BUILD_DIR/resources-sources.txt"
find android/app/src/main/java -name '*.kt' > "$BUILD_DIR/kotlin-sources.txt"
find android/app/src/main/java -name '*.java' > "$BUILD_DIR/java-sources.txt"
if [[ -s "$BUILD_DIR/java-sources.txt" ]]; then
    echo 'Production sources must be Kotlin; generated Android R.java is compiled separately.' >&2
    exit 1
fi
"$KOTLIN_DIR/bin/kotlinc" -no-jdk -no-reflect -jvm-target 1.8 -classpath "$BUILD_DIR/classes:$PLATFORM:$STDLIB" -d "$BUILD_DIR/classes" @"$BUILD_DIR/kotlin-sources.txt"
"${JAVA_BIN}jar" cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
"${JAVA_BIN}java" -cp .tools/kotlin-0300/r8.jar com.android.tools.r8.D8 --min-api 26 --lib "$PLATFORM" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar" "$STDLIB"
cp "$BUILD_DIR/resources.apk" "$BUILD_DIR/unsigned.apk"
(cd "$BUILD_DIR/dex" && zip -q "$BUILD_DIR/unsigned.apk" classes*.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
KEYSTORE="$PWD/.tools/netlens-development.jks"
python3 scripts/prepare-signing.py --keytool "${JAVA_BIN}keytool"
PASSWORD_FILE="$PWD/.tools/netlens-signing-password"
"$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias netlens --ks-pass "file:$PASSWORD_FILE" --out artifacts/Network-Lens-0.3.7.apk "$BUILD_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose artifacts/Network-Lens-0.3.7.apk
python3 scripts/check-permissions.py artifacts/Network-Lens-0.3.7.apk --aapt "$BUILD_TOOLS/aapt" --output artifacts/permissions-audit.json
python3 scripts/check-security.py artifacts/Network-Lens-0.3.7.apk --aapt "$BUILD_TOOLS/aapt" --output artifacts/security-audit.json
sha256sum artifacts/Network-Lens-0.3.7.apk > artifacts/Network-Lens-0.3.7.apk.sha256
ls -lh artifacts/Network-Lens-0.3.7.apk
