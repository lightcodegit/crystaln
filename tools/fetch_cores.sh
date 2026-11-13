#!/usr/bin/env bash
# Downloads the Xray core Android bindings (libv2ray.aar) into app/libs/.
# WireGuard comes from Maven Central and needs no manual download.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:-v26.11.9}"
DEST="app/libs/libv2ray.aar"
mkdir -p app/libs
echo "Fetching AndroidLibXrayLite ${VERSION}..."
curl -fL --retry 3 -o "$DEST" \
  "https://github.com/2dust/AndroidLibXrayLite/releases/download/${VERSION}/libv2ray.aar"
echo "Done: $DEST ($(du -h "$DEST" | cut -f1))"
