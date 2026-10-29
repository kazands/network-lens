#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .tools/sdk
fetch() {
  local name="$1" url="$2" digest="$3"
  if [[ ! -f ".tools/$name" ]]; then curl -fL --connect-timeout 20 "$url" -o ".tools/$name"; fi
  echo "$digest  .tools/$name" | sha256sum -c -
  unzip -q -o ".tools/$name" -d .tools/sdk
}
fetch platform-37.zip https://dl.google.com/android/repository/platform-37.0_r02.zip 840b23e827f96e64aea4c89a1194aac3dc5f6bad37edb231c5c795d890330e8d
fetch build-tools-36.zip https://dl.google.com/android/repository/build-tools_r36_linux.zip 5d9ac77fb6ff43d9da518a337b4fcf8f9097113df531d99ccefe80ef7ce8250b
chmod +x .tools/sdk/android-16/{aapt,aapt2,d8,zipalign,apksigner}
echo 'Local Linux Android SDK ready. Set JAVA_HOME to a full JDK, then run scripts/build.sh.'
