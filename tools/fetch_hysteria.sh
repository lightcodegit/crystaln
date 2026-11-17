#!/usr/bin/env bash
# Downloads the official Hysteria2 Android client binaries into
# app/src/main/jniLibs/<abi>/libhysteria.so (executable .so pattern, like the
# OpenVPN core). Sources: HyNetworks/hysteria release assets (app/<version>).
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:-app/v2.14.0}"
BASE="https://github.com/HyNetworks/hysteria/releases/download/${VERSION}"

# Go arch → Android ABI directory.
fetch() { # $1 = go arch name, $2 = abi dir
  local arch="$1" abi="$2"
  local dir="app/src/main/jniLibs/${abi}"
  local dest="${dir}/libhysteria.so"
  mkdir -p "$dir"
  echo "Fetching hysteria ${VERSION} (${arch})..."
  curl -fL --retry 3 -o "$dest" "${BASE}/hysteria-android-${arch}"
  chmod +x "$dest"
  echo "Done: $dest ($(du -h "$dest" | cut -f1))"
}

fetch arm64 arm64-v8a
fetch armv7 armeabi-v7a
fetch amd64 x86_64
echo "All hysteria cores fetched."
