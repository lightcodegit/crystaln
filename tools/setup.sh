#!/usr/bin/env bash
# CrystalN Linux setup: installs/uninstalls the desktop application.
#
#   tools/setup.sh install [path/to/crystaln-*.AppImage]
#   tools/setup.sh uninstall
#
# Default mode is per-user: no root required. Files:
#   ~/.local/share/crystaln/crystaln.AppImage   (the app, self-contained)
#   ~/.local/share/applications/crystaln.desktop
#   ~/.local/share/icons/hicolor/512x512/apps/crystaln.png
#
# To make it system-wide instead, run with --system (uses sudo).
set -euo pipefail

MODE="${1:-install}"
SYSTEM=false
if [ "${2:-}" = "--system" ] || [ "$MODE" = "--system" ]; then
    SYSTEM=true
fi
if [ "$MODE" = "uninstall" ] || [ "$MODE" = "-u" ]; then MODE="uninstall"; else MODE="install"; fi

APP_ID="crystaln"
APP_NAME="CrystalN"
VERSION="0.1.0"

if $SYSTEM; then
    PREFIX="/opt/crystaln"
    APPS_DIR="/usr/share/applications"
    ICONS_DIR="/usr/share/icons/hicolor/512x512/apps"
    SUDO="sudo"
else
    PREFIX="$HOME/.local/share/crystaln"
    APPS_DIR="$HOME/.local/share/applications"
    ICONS_DIR="$HOME/.local/share/icons/hicolor/512x512/apps"
    SUDO=""
fi

notice() { printf '==> %s\n' "$1"; }

install_app() {
    local src="${2:-}"
    if [ -z "$src" ]; then
        # Pick the freshest AppImage produced by the build if the user gave none.
        src=$(ls -t desktop/build/compose/binaries/main/appimage/crystaln-*.AppImage 2>/dev/null | head -n1 || true)
    fi
    if [ -z "$src" ] || [ ! -f "$src" ]; then
        cat >&2 <<EOF
AppImage не найден. Сначала соберите его:
  ./gradlew :desktop:packageAppImage
и передайте путь: tools/setup.sh install путь/к/crystaln-*.AppImage
EOF
        exit 1
    fi
    src="$(readlink -f "$src")"

    notice "Устанавливаю $APP_NAME $VERSION в $PREFIX"
    $SUDO mkdir -p "$PREFIX" "$APPS_DIR" "$ICONS_DIR"
    $SUDO rm -f "$PREFIX/$APP_ID.AppImage"
    $SUDO cp "$src" "$PREFIX/$APP_ID.AppImage"
    $SUDO chmod 755 "$PREFIX/$APP_ID.AppImage"

    $SUDO cp desktop/src/main/resources/crystaln/icon.png "$ICONS_DIR/$APP_ID.png" 2>/dev/null \
        || $SUDO rm -f "$ICONS_DIR/$APP_ID.png"

    $SUDO tee "$APPS_DIR/$APP_ID.desktop" >/dev/null <<EOF
[Desktop Entry]
Name=$APP_NAME
Comment=Мультипротокольный VPN-клиент
Exec=$PREFIX/$APP_ID.AppImage
TryExec=$PREFIX/$APP_ID.AppImage
Icon=$APP_ID
Type=Application
Categories=Network;
Terminal=false
EOF

    # AppImage hot path: some launchers won't exec a desktop entry whose
    # target lacks the executable bit after a plain copy — ensured above.
    if command -v update-desktop-database >/dev/null 2>&1; then
        $SUDO update-desktop-database "$APPS_DIR" >/dev/null 2>&1 || true
    fi
    if command -v gtk-update-icon-cache >/dev/null 2>&1; then
        $SUDO gtk-update-icon-cache -f "$ICONS_DIR" >/dev/null 2>&1 || true
    fi

    cat <<EOF
Готово! $APP_NAME установлена.
Меню приложений: «$APP_NAME».
Запуск из терминала: $PREFIX/$APP_ID.AppImage
EOF
}

uninstall_app() {
    notice "Удаляю $APP_NAME"
    $SUDO rm -f "$PREFIX/$APP_ID.AppImage"
    $SUDO rm -f "$APPS_DIR/$APP_ID.desktop"
    $SUDO rm -f "$ICONS_DIR/$APP_ID.png"
    # Settings/profiles deliberately kept (they belong to the user, not the app copy).
    if command -v update-desktop-database >/dev/null 2>&1; then
        update-desktop-database "$APPS_DIR" >/dev/null 2>&1 || true
    fi
    echo "Удалено. Конфигурация профилей сохранена."
}

case "$MODE" in
    install) install_app "$@" ;;
    uninstall) uninstall_app ;;
esac
