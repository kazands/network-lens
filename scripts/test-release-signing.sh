#!/usr/bin/env bash
set -euo pipefail
umask 077
cd "$(dirname "$0")/.."
python3 scripts/setup-kotlin-toolchain.py
TEST_DIR="$PWD/.tools/release-signing-tests"
mkdir -p "$TEST_DIR/classes"
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}"
KOTLIN_DIR="$PWD/.tools/kotlin-0300/kotlinc"
"$KOTLIN_DIR/bin/kotlinc" -Xjdk-release=8 -jvm-target 1.8 -no-reflect \
    -d "$TEST_DIR/classes" tests/release/SigningTest.kt
"${JAVA_BIN}java" -cp "$TEST_DIR/classes:$KOTLIN_DIR/lib/kotlin-stdlib.jar" SigningTest
