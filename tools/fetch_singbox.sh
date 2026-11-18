#!/usr/bin/env bash
# Downloads the official sing-box binaries into:
#   - app/src/main/jniLibs/<abi>/libsingbox.so (Android, executable .so pattern)
#   - desktop bin dir is populated at runtime by Binaries (not here).
# sing-box powers TUIC / AnyTLS / Hysteria v1 (and could replace hysteria).
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:-1.14.4}"
BASE="https://github.com/SagerNet/sing-box/releases/download/v${VERSION}"

fetch_android() { # $1 = sing-box arch name, $2 = abi dir
  local arch="$1" abi="$2"
  local dir="app/src/main/jniLibs/${abi}"
  local dest="${dir}/libsingbox.so"
  mkdir -p "$dir"
  local tmp
  tmp="$(mktemp -d)"
  echo "Fetching sing-box v${VERSION} (${arch})..."
  curl -fL --retry 3 -o "${tmp}/sb.tar.gz" "${BASE}/sing-box-${VERSION}-android-${arch}.tar.gz"
  tar -xzf "${tmp}/sb.tar.gz" -C "${tmp}"
  local bin
  bin="$(ls ${tmp}/sing-box-*/sing-box 2>/dev/null | head -n1)"
  if [[ -z "$bin" ]]; then echo "sing-box binary not found in archive"; exit 1; fi
  cp "$bin" "$dest"
  chmod +x "$dest"
  rm -rf "$tmp"
  echo "Done: $dest"
}

fetch_android arm64 arm64-v8a
fetch_android arm armeabi-v7a
fetch_android amd64 x86_64
echo "All sing-box cores fetched."
