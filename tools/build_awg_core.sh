#!/usr/bin/env bash
# Builds the REAL AmneziaWG core (amneziawg-go) for Android from source and
# packs it into app/libs/amneziawg.aar.
#
# Requirements on the build host:
#   - Android SDK with platforms;35 (licenses accepted)
#   - Android NDK (any recent; the build below uses r26b layout)
#   - Go 1.25+ for Windows/Linux host
#   - bash (Git Bash on Windows works; paths below are converted for MSYS)
#
# Usage:  bash tools/build_awg_core.sh
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)
AWG=third_party/amneziawg-android

# ---- host tool discovery -----------------------------------------------------
GO_BIN="${GO_BIN:-go}"
NDK="${NDK:-$ANDROID_NDK_HOME}"
if [ -z "$NDK" ]; then
  for d in /d/Android/Sdk/ndk/*/ "$ANDROID_HOME/ndk/"*/; do
    [ -d "$d" ] && NDK="$d" && break
  done
fi
SDK="${SDK:-$ANDROID_HOME}"
[ -z "$SDK" ] && SDK=$(grep sdk.dir local.properties 2>/dev/null | cut -d= -f2 | tr -d '\r' | sed 's/\\\\/\\\\/g; s/\\:/\:/')
echo "Using NDK=$NDK SDK=$SDK GO=$GO_BIN"

# ---- 1. clone ----------------------------------------------------------------
if [ ! -d "$AWG" ]; then
  mkdir -p third_party
  git clone --depth 1 --recurse-submodules --shallow-submodules \
    https://github.com/amnezia-vpn/amneziawg-android.git "$AWG"
fi

# ---- 2. patch wrapper for modern JDKs (Java 25 hosts) -------------------------
sed -i 's/gradle-8.13-bin.zip/gradle-9.5.1-bin.zip/' "$AWG/gradle/wrapper/gradle-wrapper.properties" || true
echo "sdk.dir=$(echo "$SDK" | sed 's/\\/:/g' | sed 's|^D:|D:/|; s|\\|/|g')" > "$AWG/local.properties" || true

# ---- 3. patch CMake: no host cc (elf-cleaner), prebuilt libwg-go --------------
CMAKE="$AWG/tunnel/tools/CMakeLists.txt"
python - "$CMAKE" <<'PYEOF'
import sys, re
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
s = re.sub(r"add_custom_target\(libwg-go\.so.*?\)\n\n# Strip", """add_custom_target(libwg-go.so WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/libwg-go" COMMENT "Copying prebuilt amneziawg-go" VERBATIM COMMAND
    "${CMAKE_COMMAND}" -E copy_if_different
    "${CMAKE_CURRENT_SOURCE_DIR}/libwg-go/prebuilt/${ANDROID_ABI}/libwg-go.so"
    "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libwg-go.so")

# Strip""", s, flags=re.S)
s = re.sub(r"add_custom_target\(elf-cleaner.*?add_dependencies\(libwg-quick\.so elf-cleaner\)\n", "", s, flags=re.S)
open(p, 'w', encoding='utf-8', newline='').write(s)
print("cmake patched")
PYEOF

# ---- 4. build libwg-go.so (amneziawg-go userspace core) per ABI ---------------
cd "$AWG/tunnel/tools/libwg-go"
mkdir -p prebuilt
build_abi() {
  ABI=$1; TRIPLE=$2; ARCH=$3
  export GOOS=android GOARCH=$ARCH CGO_ENABLED=1
  export CC="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
  export CGO_CFLAGS="--target=$TRIPLE --sysroot=$NDK/toolchains/llvm/prebuilt/windows-x86_64/sysroot -DANDROID -fdata-sections -ffunction-sections -funwind-tables -fstack-protector-strong -no-canonical-prefixes -D_FORTIFY_SOURCE=2 -Wformat -Werror=format-security"
  export CGO_LDFLAGS="--target=$TRIPLE --sysroot=$NDK/toolchains/llvm/prebuilt/windows-x86_64/sysroot -static-libstdc++ -Wl,--build-id=sha1 -Wl,--no-rosegment -Wl,--no-undefined-version -Wl,--no-undefined -Qunused-arguments -Wl,-z,max-page-size=16384"
  mkdir -p "prebuilt/$ABI"
  "$GO_BIN" build -tags linux \
    -ldflags="-X github.com/amnezia-vpn/amneziawg-go/v3/ipc.socketDirectory=/data/data/com.amneziawg.android/cache/amneziawg -buildid=" \
    -trimpath -buildvcs=false -buildmode c-shared -o "prebuilt/$ABI/libwg-go.so" .
  echo "built $ABI"
}
build_abi arm64-v8a  aarch64-linux-android24    arm64
build_abi armeabi-v7a armv7a-linux-androideabi24 arm
build_abi x86        i686-linux-android24        386
build_abi x86_64     x86_64-linux-android24      amd64
cd "$ROOT"

# ---- 5. assemble the tunnel AAR ----------------------------------------------
(cd "$AWG" && ./gradlew :tunnel:assembleRelease -PamneziawgPackageName=com.amneziawg.android --console=plain)

# ---- 6. install into the app --------------------------------------------------
mkdir -p app/libs
cp "$AWG/tunnel/build/outputs/aar/tunnel-release.aar" app/libs/amneziawg.aar
echo "OK: app/libs/amneziawg.aar is ready ($(du -h app/libs/amneziawg.aar | cut -f1))"
