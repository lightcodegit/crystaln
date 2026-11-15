#!/usr/bin/env bash
# Builds a portable CrystalN AppImage (works on any glibc Linux with FUSE).
#
# Usage: ./gradlew :desktop:packageAppImage
# Requires: createDistributable already built (the gradle task depends on it),
#           network access on first run (downloads appimagetool from GitHub).
set -euo pipefail

cd "$(dirname "$0")/.."
# mksquashfs is needed by appimagetool; prefer a user-local copy.
export PATH="$HOME/.local/bin:$PATH"
VERSION="${VERSION:-1.0.0}"
ARCH="${ARCH:-x86_64}"
DIST="desktop/build/compose/binaries/main/appimage"
APPDIR="$DIST/CrystalN.AppDir"
TOOL="$HOME/.cache/crystaln/tools/appimagetool-$ARCH.AppImage"

SRC="desktop/build/compose/binaries/main/app/crystaln"
if [ ! -x "$SRC/bin/crystaln" ]; then
    echo "error: $SRC/bin/crystaln not found — run './gradlew :desktop:createDistributable'" >&2
    exit 1
fi

# appimagetool: download once into the user's cache.
if [ ! -f "$TOOL" ]; then
    mkdir -p "$(dirname "$TOOL")"
    echo ">> скачиваю appimagetool…"
    curl -fL --retry 3 -o "$TOOL" \
        "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-$ARCH.AppImage"
    chmod +x "$TOOL"
fi

# FUSE may be unavailable (containers, minimal hosts) — the extract-run mode
# needs no FUSE at all, it just unpacks the tool image first.
run_tool() {
    if [ -n "${APPIMAGE_EXTRACT_AND_RUN:-}" ] || ! command -v fusermount >/dev/null 2>&1; then
        APPIMAGE_EXTRACT_AND_RUN=1 "$TOOL" "$@"
    else
        "$TOOL" "$@"
    fi
}

rm -rf "$APPDIR" "$DIST/crystaln-$VERSION-$ARCH.AppImage"
mkdir -p "$APPDIR/usr/share/icons/hicolor/512x512/apps"

# The jpackage distributable is relocatable: its launcher resolves lib/ and
# runtime/ relative to its own real path, so it can live anywhere under APPDIR.
cp -a "$SRC" "$APPDIR/usr/crystaln"

cp desktop/src/main/resources/crystaln/icon.png "$APPDIR/usr/share/icons/hicolor/512x512/apps/crystaln.png"
cp desktop/src/main/resources/crystaln/icon.png "$APPDIR/crystaln.png"

cat > "$APPDIR/crystaln.desktop" <<EOF
[Desktop Entry]
Name=CrystalN
Comment=Мультипротокольный VPN-клиент
Exec=crystaln
Icon=crystaln
Type=Application
Categories=Network;
Terminal=false
StartupWMClass=crystaln
EOF

cat > "$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/crystaln/bin/crystaln" "$@"
EOF
chmod +x "$APPDIR/AppRun"

echo ">> собираю AppImage…"
run_tool "$APPDIR" "$DIST/crystaln-$VERSION-$ARCH.AppImage"

echo ">> готово: $DIST/crystaln-$VERSION-$ARCH.AppImage"
