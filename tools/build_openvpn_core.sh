#!/usr/bin/env bash
# Builds the official OpenVPN core for Android (TARGET_ANDROID build, managed
# tun via the management interface) and installs:
#
#   app/src/main/jniLibs/<abi>/libovpnexec.so    — openvpn executable
#
# The core is built from the official openvpn release tarball together with
# static OpenSSL, LZO and LZ4. Everything is linked statically into the
# openvpn executable; no extra shared libs ship in the APK. The executable
# is shipped as "lib*.so" so gradle extracts it to nativeLibraryDir, from
# where OpenVpnService launches it as a child process (ics-openvpn style).
#
# Requirements on the build host:
#   - Android NDK (r19+; wrapper clang compilers)
#   - curl, tar, make, sed, pkg-config (for the configure sanity checks)
#
# Usage:  bash tools/build_openvpn_core.sh
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)

OPENVPN_VERSION=2.6.23
OPENSSL_VERSION=3.2.2
LZO_VERSION=2.10
LZ4_VERSION=1.9.4
ANDROID_API=24

# ---- host tool discovery -------------------------------------------------------
NDK="${NDK:-${ANDROID_NDK_HOME:-}}"
if [ -z "$NDK" ]; then
  for d in "${ANDROID_HOME:-/nonexistent}/ndk/"*/ "$HOME/Android/Sdk/ndk/"*/; do
    [ -d "$d" ] && NDK="$d" && break
  done
fi
SDK="${SDK:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ] && [ -f local.properties ]; then
  SDK=$(grep sdk.dir local.properties | cut -d= -f2 | tr -d '\r' | sed 's|\\\\|/|g; s|\\:|:|; s|\\|/|g')
fi
if [ -z "$NDK" ] && [ -n "$SDK" ]; then
  for d in "$SDK/ndk/"*/; do
    [ -d "$d" ] && NDK="$d" && break
  done
fi
[ -n "$NDK" ] || { echo "ERROR: Android NDK not found (set NDK=...)"; exit 1; }

HOST_TAG=$(basename "$(echo "$NDK"/toolchains/llvm/prebuilt/*/ | tr ' ' '\n' | head -1)")
TC="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
echo "Using NDK=$NDK host=$HOST_TAG"

# ABI -> triple / openssl target
abi_cc() {
  case "$1" in
    arm64-v8a)   echo "$TC/aarch64-linux-android${ANDROID_API}-clang" ;;
    armeabi-v7a) echo "$TC/armv7a-linux-androideabi${ANDROID_API}-clang" ;;
    x86_64)      echo "$TC/x86_64-linux-android${ANDROID_API}-clang" ;;
  esac
}
abi_triple() {
  case "$1" in
    arm64-v8a)   echo "aarch64-linux-android" ;;
    armeabi-v7a) echo "armv7a-linux-androideabi" ;;
    x86_64)      echo "x86_64-linux-android" ;;
  esac
}
# Windows hosts ship .cmd wrappers; gradle/jniLibs naming wants the plain name.
pick_cc() {
  local c=$(abi_cc "$1")
  if [ -f "$c" ]; then echo "$c"; elif [ -f "$c.cmd" ]; then echo "$c.cmd"; else echo "$c"; fi
}
ABIS=(arm64-v8a armeabi-v7a x86_64)

BUILD=third_party/build/openvpn-core
DOWNLOADS="$ROOT/downloads"
mkdir -p "$BUILD" "$DOWNLOADS"

fetch() {
  local url="$1" out="$DOWNLOADS/$2"
  [ -f "$out" ] && { echo "have $out"; return; }
  echo "downloading $url"
  curl -fL --retry 3 -o "$out" "$url"
}

fetch "https://swupdate.openvpn.org/community/releases/openvpn-${OPENVPN_VERSION}.tar.gz" "openvpn-${OPENVPN_VERSION}.tar.gz"
fetch "https://github.com/openssl/openssl/releases/download/openssl-${OPENSSL_VERSION}/openssl-${OPENSSL_VERSION}.tar.gz" "openssl-${OPENSSL_VERSION}.tar.gz"
fetch "https://www.oberhumer.com/opensource/lzo/download/lzo-${LZO_VERSION}.tar.gz" "lzo-${LZO_VERSION}.tar.gz"
fetch "https://github.com/lz4/lz4/archive/refs/tags/v${LZ4_VERSION}.tar.gz" "lz4-${LZ4_VERSION}.tar.gz"

cd "$BUILD"
for f in openvpn-${OPENVPN_VERSION} openssl-${OPENSSL_VERSION} lzo-${LZO_VERSION}; do
  [ -d "$f" ] || tar -xzf "$DOWNLOADS/$f.tar.gz"
done
[ -d "lz4-${LZ4_VERSION}" ] || tar -xzf "$DOWNLOADS/lz4-${LZ4_VERSION}.tar.gz"
cd "$ROOT"

build_abi() {
  local ABI=$1
  local TRIPLE=$(abi_triple "$ABI")
  local CC=$(pick_cc "$ABI")
  local PREFIX="$ROOT/$BUILD/install/$ABI"
  # Export for the whole function: an inline `PATH=... cmd && make ...` only
  # applies to the first command, so parallel make recipes would lose the
  # toolchain from PATH.
  export PATH="$TC:$PATH"
  export ANDROID_NDK_HOME="$NDK" ANDROID_NDK_ROOT="$NDK"
  mkdir -p "$PREFIX"

  echo "== $ABI: openssl =="
  if [ ! -f "$PREFIX/lib/libssl.a" ]; then
    (cd "$BUILD/openssl-${OPENSSL_VERSION}" &&
      if [ -f Makefile ]; then make clean >/dev/null 2>&1 || true; fi
      ./Configure "android-$(case $ABI in arm64-v8a) echo arm64;; armeabi-v7a) echo arm;; x86_64) echo x86_64;; esac)" \
        -D__ANDROID_API__=$ANDROID_API no-shared no-tests no-docs \
        --prefix="$PREFIX" --openssldir="$PREFIX" &&
      make -j"$(nproc 2>/dev/null || echo 4)" build_sw && make install_sw)
  fi

  echo "== $ABI: lzo =="
  if [ ! -f "$PREFIX/lib/liblzo2.a" ]; then
    (cd "$BUILD/lzo-${LZO_VERSION}" &&
      if [ -f Makefile ]; then make distclean >/dev/null 2>&1 || make clean >/dev/null 2>&1 || true; fi
      CC="$CC" ./configure --host="$TRIPLE" --enable-static --disable-shared \
        --prefix="$PREFIX" &&
      make -j"$(nproc 2>/dev/null || echo 4)" && make install)
  fi

  echo "== $ABI: lz4 =="
  if [ ! -f "$PREFIX/lib/liblz4.a" ]; then
    mkdir -p "$PREFIX/lib" "$PREFIX/include"
    for f in lz4 lz4hc xxhash; do
      "$CC" -O2 -fPIC -I"$BUILD/lz4-${LZ4_VERSION}/lib" -c \
        "$BUILD/lz4-${LZ4_VERSION}/lib/$f.c" -o "$PREFIX/lib/$f.o"
    done &&
    "$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin/llvm-ar" rcs "$PREFIX/lib/liblz4.a" \
      "$PREFIX"/lib/{lz4,lz4hc,xxhash}.o &&
    cp "$BUILD/lz4-${LZ4_VERSION}/lib/lz4.h" \
       "$BUILD/lz4-${LZ4_VERSION}/lib/lz4hc.h" \
       "$BUILD/lz4-${LZ4_VERSION}/lib/xxhash.h" "$PREFIX/include/" &&
    rm -f "$PREFIX"/lib/{lz4,lz4hc,xxhash}.o
  fi

  echo "== $ABI: openvpn =="
  if [ ! -f "$BUILD/openvpn-${OPENVPN_VERSION}/src/openvpn/openvpn-$ABI" ]; then
    (cd "$BUILD/openvpn-${OPENVPN_VERSION}" &&
      [ -f config.h ] && { make distclean >/dev/null 2>&1 || true; } || true
      # The configure host triplet is *-*-linux*: it selects TARGET_LINUX
      # (netlink-based ifconfig/route) and requires libcap-ng unconditionally.
      # On Android the tun is set up through the management interface instead,
      # so those defines are stripped from config.h after configure. Empty
      # (but non-null) LIBCAPNG_* flags skip the pkg-config lookup; the
      # HAVE_LIBCAPNG define is removed below so nothing calls capng_*.
      # NOTE: keep comments on their own lines — a comment after a trailing
      # backslash terminates the logical line and silently drops the env
      # assignments that precede it.
      CC="$CC" \
      CPPFLAGS="-DTARGET_ANDROID" \
      OPENSSL_CFLAGS="-I$PREFIX/include" OPENSSL_LIBS="-L$PREFIX/lib -lssl -lcrypto" \
      LZO_CFLAGS="-I$PREFIX/include" LZO_LIBS="-L$PREFIX/lib -llzo2" \
      LZ4_CFLAGS="-I$PREFIX/include" LZ4_LIBS="-L$PREFIX/lib -llz4" \
      LIBCAPNG_CFLAGS=" " LIBCAPNG_LIBS=" " \
      ./configure --host="$TRIPLE" --prefix="$PREFIX" \
        --disable-dco --disable-plugins --disable-plugin-auth-pam \
        --disable-systemd --enable-management &&
      sed -i -e '/#define TARGET_LINUX 1/d' \
             -e '/#define ENABLE_SITNL 1/d' \
             -e '/#define ENABLE_DCO 1/d' \
             -e '/#define HAVE_LIBCAPNG 1/d' \
             -e '/#define ENABLE_FEATURE_TUN_PERSIST 1/d' \
             -e 's/^#define TARGET_PREFIX.*/#define TARGET_PREFIX "A"/' \
             config.h &&
      echo '#define TARGET_ANDROID 1' >> config.h &&
      make -j"$(nproc 2>/dev/null || echo 4)" &&
      cp src/openvpn/openvpn "src/openvpn/openvpn-$ABI")
  fi

  echo "== $ABI: install =="
  mkdir -p "app/src/main/jniLibs/$ABI"
  cp "$BUILD/openvpn-${OPENVPN_VERSION}/src/openvpn/openvpn-$ABI" \
    "app/src/main/jniLibs/$ABI/libovpnexec.so"
}

for abi in "${ABIS[@]}"; do
  build_abi "$abi"
done

echo "OK: libovpnexec.so installed into app/src/main/jniLibs/"
echo "    Rebuild the app (./gradlew assembleDebug) to pick them up."
