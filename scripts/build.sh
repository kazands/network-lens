#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."
BUILD_MODE=development
python3 scripts/generate-recog-models.py --check
python3 scripts/generate-apple-models.py --check
python3 scripts/generate-android-models.py --check
python3 scripts/generate-product-models.py --check
python3 scripts/generate-android-hardware.py --check
python3 scripts/generate-discovery-matchers.py --check
python3 scripts/check-recognition-coverage.py
python3 scripts/update-matter-models.py --check
if [[ $# -eq 1 && "$1" == --release ]]; then
    BUILD_MODE=release
elif [[ $# -ne 0 ]]; then
    echo 'Usage: bash scripts/build.sh [--release]' >&2
    exit 1
fi
if [[ "$BUILD_MODE" == release ]]; then
    # Validate the pinned key and recovery receipt before any build work or downloads.
    python3 scripts/release-signing.py check --role app
fi
SDK_DIR="${ANDROID_SDK_ROOT:-$PWD/.tools/sdk}"
BUILD_TOOLS="${NETWORK_LENS_BUILD_TOOLS:-$SDK_DIR/android-16}"
if [[ ! -x "$BUILD_TOOLS/aapt2" ]]; then BUILD_TOOLS="$SDK_DIR/build-tools/36.0.0"; fi
PLATFORM="$SDK_DIR/android-37.0/android.jar"
if [[ ! -f "$PLATFORM" ]]; then PLATFORM="$SDK_DIR/platforms/android-37.0/android.jar"; fi
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
if ! command -v "${JAVA_BIN}javac" >/dev/null; then echo 'Set JAVA_HOME to a full JDK 17 or later.' >&2; exit 1; fi
if [[ ! -f "$PLATFORM" || ! -x "$BUILD_TOOLS/aapt2" ]]; then echo 'Android SDK platform 37.0 and build-tools 36.0.0 are required. See README.md.' >&2; exit 1; fi
BUILD_DIR="$PWD/.tools/build"
APK_DIR="$PWD/artifacts"
if [[ "$BUILD_MODE" == release ]]; then BUILD_DIR="$PWD/.tools/build-release"; APK_DIR="$PWD/artifacts/release"; fi
APK_PATH="$APK_DIR/Network-Lens-0.3.35.apk"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/generated" "$BUILD_DIR/dex" artifacts
# Remove only this script's generated output so removed source files cannot survive a rebuild.
find "$BUILD_DIR/classes" "$BUILD_DIR/generated" "$BUILD_DIR/dex" -type f -delete
python3 scripts/setup-compose-runtime.py
COMPOSE_RUNTIME="$PWD/.tools/compose-build/runtime"
RESOURCE_OVERLAYS=()
RESOURCE_INDEX=0
while IFS= read -r RESOURCE_DIR; do
    [[ -n "$RESOURCE_DIR" ]] || continue
    RESOURCE_INDEX=$((RESOURCE_INDEX + 1))
    RESOURCE_ZIP="$BUILD_DIR/compose-res-$RESOURCE_INDEX.zip"
    "$BUILD_TOOLS/aapt2" compile --dir "$RESOURCE_DIR" -o "$RESOURCE_ZIP"
    RESOURCE_OVERLAYS+=(-R "$RESOURCE_ZIP")
done < "$COMPOSE_RUNTIME/resources.txt"
"$BUILD_TOOLS/aapt2" compile --dir android/app/src/main/res -o "$BUILD_DIR/resources.zip"
sed 's/<manifest /<manifest package="io.github.kazands.networklens" /' android/app/src/main/AndroidManifest.xml > "$BUILD_DIR/AndroidManifest.xml"
# Deliberately keep only the app's reviewed components. AndroidX startup/profile/emoji
# initializers are optional and are not merged into the application manifest.
"$BUILD_TOOLS/aapt2" link --auto-add-overlay --extra-packages "$(cat "$COMPOSE_RUNTIME/packages.txt")" \
    -o "$BUILD_DIR/resources.apk" --manifest "$BUILD_DIR/AndroidManifest.xml" -I "$PLATFORM" \
    --java "$BUILD_DIR/generated" "${RESOURCE_OVERLAYS[@]}" -R "$BUILD_DIR/resources.zip"
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
COMPOSE_CLASSPATH="$(cat "$COMPOSE_RUNTIME/classpath.txt")"
"$KOTLIN_DIR/bin/kotlinc" -no-jdk -no-reflect -jvm-target 11 \
    -Xplugin="$KOTLIN_DIR/lib/compose-compiler-plugin.jar" \
    -classpath "$BUILD_DIR/classes:$PLATFORM:$COMPOSE_CLASSPATH" -d "$BUILD_DIR/classes" @"$BUILD_DIR/kotlin-sources.txt"
"${JAVA_BIN}jar" cf "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
mapfile -t RUNTIME_JARS < "$COMPOSE_RUNTIME/jars.txt"
"${JAVA_BIN}java" -Xmx2g -cp .tools/kotlin-0300/r8.jar com.android.tools.r8.D8 --min-api 26 --lib "$PLATFORM" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar" "${RUNTIME_JARS[@]}"
cp "$BUILD_DIR/resources.apk" "$BUILD_DIR/unsigned.apk"
(cd "$BUILD_DIR/dex" && zip -q "$BUILD_DIR/unsigned.apk" classes*.dex)
(cd "$COMPOSE_RUNTIME/native" && zip -q -0 -r "$BUILD_DIR/unsigned.apk" lib)
"$BUILD_TOOLS/zipalign" -f -P 16 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
KEYSTORE="$PWD/.tools/network-lens-development.jks"
PASSWORD_FILE="$PWD/.tools/network-lens-signing-password"
if [[ "$BUILD_MODE" == release ]]; then
    python3 scripts/release-signing.py sign-apk --input "$BUILD_DIR/aligned.apk" --output "$APK_PATH"
else
    python3 scripts/prepare-signing.py --keytool "${JAVA_BIN}keytool"
    "$BUILD_TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias network-lens --ks-pass "file:$PASSWORD_FILE" --out "$APK_PATH" "$BUILD_DIR/aligned.apk"
fi
"$BUILD_TOOLS/apksigner" verify --verbose "$APK_PATH"
"$BUILD_TOOLS/zipalign" -c -P 16 4 "$APK_PATH"
python3 scripts/check-permissions.py "$APK_PATH" --aapt "$BUILD_TOOLS/aapt" --output "$APK_DIR/permissions-audit.json"
python3 scripts/check-security.py "$APK_PATH" --aapt "$BUILD_TOOLS/aapt" --output "$APK_DIR/security-audit.json"
sha256sum "$APK_PATH" > "$APK_PATH.sha256"
ls -lh "$APK_PATH"
