#!/bin/bash
#
# build-aria2.sh - Cross-compile aria2 for Android using NDK
#
# This script builds aria2 from source for all supported Android ABIs.
# It is designed to be version-agnostic: change ARIA2_VERSION to upgrade.
#
# Prerequisites:
#   - Android NDK (set ANDROID_NDK_HOME or use default path)
#   - autoconf, automake, libtool, make, git, curl
#
# Usage:
#   ./scripts/build-aria2.sh [--version 1.37.0] [--abi arm64-v8a]
#
# Output:
#   app/src/main/jniLibs/{abi}/libaria2c.so
#

set -euo pipefail

# ============================================================
# CONFIGURATION - Change ARIA2_VERSION to upgrade aria2
# ============================================================
ARIA2_VERSION="${ARIA2_VERSION:-1.37.0}"
ARIA2_REPO="https://github.com/aria2/aria2.git"

# Android NDK path (auto-detect or use env var)
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-/opt/android-sdk/ndk/28.2.13676358}"
NDK_TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"

# Minimum Android API level
MIN_API=24

# All supported ABIS
ALL_ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")

# Build directory
BUILD_DIR="$(pwd)/aria2-build"
OUTPUT_DIR="$(pwd)/app/src/main/jniLibs"

# Dependencies
OPENSSL_VERSION="1.1.1w"
ZLIB_VERSION="1.3.1"

# ============================================================
# Parse arguments
# ============================================================
SELECTED_ABIS=()
while [[ $# -gt 0 ]]; do
    case $1 in
        --version) ARIA2_VERSION="$2"; shift 2 ;;
        --abi) SELECTED_ABIS+=("$2"); shift 2 ;;
        --ndk) ANDROID_NDK_HOME="$2"; NDK_TOOLCHAIN="$2/toolchains/llvm/prebuilt/linux-x86_64"; shift 2 ;;
        --clean) rm -rf "$BUILD_DIR"; echo "Cleaned build directory"; exit 0 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

if [ ${#SELECTED_ABIS[@]} -eq 0 ]; then
    SELECTED_ABIS=("${ALL_ABIS[@]}")
fi

# ============================================================
# Utility functions
# ============================================================
log() { echo -e "\033[1;32m[build-aria2]\033[0m $1"; }
error() { echo -e "\033[1;31m[build-aria2] ERROR:\033[0m $1"; exit 1; }

get_api_level() {
    local abi="$1"
    case "$abi" in
        arm64-v8a) echo "21" ;;
        armeabi-v7a) echo "21" ;;
        x86_64) echo "21" ;;
        *) echo "$MIN_API" ;;
    esac
}

get_target() {
    local abi="$1"
    case "$abi" in
        arm64-v8a) echo "aarch64-linux-android" ;;
        armeabi-v7a) echo "arm-linux-androideabi" ;;
        x86_64) echo "x86_64-linux-android" ;;
    esac
}

get_host() {
    local abi="$1"
    case "$abi" in
        arm64-v8a) echo "aarch64-linux-android" ;;
        armeabi-v7a) echo "armv7a-linux-androideabi" ;;
        x86_64) echo "x86_64-linux-android" ;;
    esac
}

# ============================================================
# Check prerequisites
# ============================================================
check_prerequisites() {
    log "Checking prerequisites..."

    [ -d "$NDK_TOOLCHAIN" ] || error "NDK toolchain not found at $NDK_TOOLCHAIN"
    command -v autoconf >/dev/null || error "autoconf not found"
    command -v automake >/dev/null || error "automake not found"
    command -v libtool >/dev/null || error "libtool not found"
    command -v git >/dev/null || error "git not found"

    log "All prerequisites found."
}

# ============================================================
# Download aria2 source
# ============================================================
download_aria2() {
    local src_dir="$BUILD_DIR/aria2-$ARIA2_VERSION"

    if [ -d "$src_dir" ]; then
        log "aria2 source already exists at $src_dir"
        return 0
    fi

    log "Downloading aria2 v$ARIA2_VERSION..."
    mkdir -p "$BUILD_DIR"

    if [ -d "$BUILD_DIR/aria2" ]; then
        cd "$BUILD_DIR/aria2"
        git fetch --tags
    else
        git clone "$ARIA2_REPO" "$BUILD_DIR/aria2"
        cd "$BUILD_DIR/aria2"
    fi

    git checkout "release-$ARIA2_VERSION" 2>/dev/null || git checkout "v$ARIA2_VERSION" 2>/dev/null || git checkout "$ARIA2_VERSION"
    log "aria2 v$ARIA2_VERSION source ready."
}

# ============================================================
# Build aria2 for a specific ABI
# ============================================================
build_for_abi() {
    local abi="$1"
    local target=$(get_target "$abi")
    local host=$(get_host "$abi")
    local api_level=$(get_api_level "$abi")
    local prefix="$BUILD_DIR/prefix/$abi"
    local work_dir="$BUILD_DIR/work/$abi"

    log "========================================="
    log "Building aria2 for $abi (target: $target, API: $api_level)"
    log "========================================="

    mkdir -p "$prefix" "$work_dir"

    # Environment variables for NDK cross-compilation
    export CC="$NDK_TOOLCHAIN/bin/${target}${api_level}-clang"
    export CXX="$NDK_TOOLCHAIN/bin/${target}${api_level}-clang++"
    export AR="$NDK_TOOLCHAIN/bin/llvm-ar"
    export AS="$NDK_TOOLCHAIN/bin/llvm-as"
    export LD="$NDK_TOOLCHAIN/bin/ld.lld"
    export RANLIB="$NDK_TOOLCHAIN/bin/llvm-ranlib"
    export STRIP="$NDK_TOOLCHAIN/bin/llvm-strip"
    export NM="$NDK_TOOLCHAIN/bin/llvm-nm"
    export CFLAGS="-O2 -fPIC"
    export CXXFLAGS="-O2 -fPIC"
    export LDFLAGS="-L$prefix/lib"

    cd "$work_dir"

    # Clone aria2 source for this ABI build
    if [ ! -d "aria2" ]; then
        cp -r "$BUILD_DIR/aria2" aria2
    fi
    cd aria2
    make distclean 2>/dev/null || true
    git checkout -- . 2>/dev/null || true
    autoreconf -fiv

    # Build OpenSSL
    log "Building OpenSSL..."
    local openssl_dir="$BUILD_DIR/openssl-src"
    if [ ! -d "$openssl_dir" ]; then
        mkdir -p "$BUILD_DIR"
        git clone --depth 1 --branch OpenSSL_1_1_1w https://github.com/openssl/openssl.git "$openssl_dir" 2>/dev/null || {
            curl -fsSL "https://github.com/openssl/openssl/releases/download/OpenSSL_1_1_1w/openssl-1.1.1w.tar.gz" -o /tmp/openssl.tar.gz
            mkdir -p "$openssl_dir"
            tar xzf /tmp/openssl.tar.gz -C "$openssl_dir" --strip-components=1
        }
    fi
    local openssl_build="$BUILD_DIR/openssl/$abi"
    mkdir -p "$openssl_build"
    cd "$openssl_dir"
    make clean 2>/dev/null || true
    case "$abi" in
        arm64-v8a)  openssl_target="android-aarch64" ;;
        armeabi-v7a) openssl_target="android-arm" ;;
        x86_64)     openssl_target="android-x86_64" ;;
    esac
    PATH="$NDK_TOOLCHAIN/bin:$PATH" ./Configure "$openssl_target" -D__ANDROID_API__=$api_level --prefix="$prefix" no-shared no-tests no-asm
    PATH="$NDK_TOOLCHAIN/bin:$PATH" make -j$(nproc) 2>&1 | tail -5
    PATH="$NDK_TOOLCHAIN/bin:$PATH" make install_sw 2>&1 | tail -3

    cd "$work_dir/aria2"

    # Configure aria2 with OpenSSL (for HTTPS support)
    ./configure \
        --host="$target" \
        --prefix="$prefix" \
        --with-openssl="$prefix" \
        --without-gnutls \
        --without-libnettle \
        --without-libxml2 \
        --without-libexpat \
        --without-sqlite3 \
        --without-libssh2 \
        --without-zlib \
        --without-libcares \
        --without-java \
        --disable-bittorrent \
        --disable-metalink \
        --disable-websocket \
        --disable-xml-rpc \
        --enable-threads \
        --disable-nls \
        --disable-silent-rules

    log "Configured. Building..."
    make -j$(nproc)
    make install

    # Copy binary to output (must use .so extension for jniLibs)
    local output_abi_dir="$OUTPUT_DIR/$abi"
    mkdir -p "$output_abi_dir"
    cp "$prefix/bin/aria2c" "$output_abi_dir/libaria2c.so"
    chmod +x "$output_abi_dir/libaria2c.so"

    # Verify the binary
    file "$output_abi_dir/libaria2c.so"
    "$output_abi_dir/libaria2c.so" --version 2>/dev/null || log "(Cross-compiled binary cannot run on host - this is expected)"

    log "aria2 for $abi built successfully!"
    log "Binary: $output_abi_dir/libaria2c.so"

    # Clean environment
    unset CC CXX AR AS LD RANLIB STRIP NM CFLAGS CXXFLAGS LDFLAGS
}

# ============================================================
# Build with BT/Magnet support (optional, needs dependencies)
# ============================================================
build_with_bt_support() {
    local abi="$1"
    local target=$(get_target "$abi")
    local api_level=$(get_api_level "$abi")
    local prefix="$BUILD_DIR/prefix-bt/$abi"
    local work_dir="$BUILD_DIR/work-bt/$abi"
    local deps_dir="$BUILD_DIR/deps/$abi"

    log "Building aria2 with BT support for $abi..."

    mkdir -p "$prefix" "$work_dir" "$deps_dir"

    export CC="$NDK_TOOLCHAIN/bin/${target}${api_level}-clang"
    export CXX="$NDK_TOOLCHAIN/bin/${target}${api_level}-clang++"
    export AR="$NDK_TOOLCHAIN/bin/llvm-ar"
    export RANLIB="$NDK_TOOLCHAIN/bin/llvm-ranlib"
    export STRIP="$NDK_TOOLCHAIN/bin/llvm-strip"
    export CFLAGS="-O2 -fPIC"
    export LDFLAGS="-L$prefix/lib"

    # Build zlib
    log "Building zlib..."
    cd "$deps_dir"
    if [ ! -d "zlib-$ZLIB_VERSION" ]; then
        curl -fsSL "https://zlib.net/zlib-$ZLIB_VERSION.tar.gz" -o zlib.tar.gz
        tar xzf zlib.tar.gz
    fi
    cd "zlib-$ZLIB_VERSION"
    CC="$CC" AR="$AR" RANLIB="$RANLIB" ./configure --static --prefix="$prefix"
    make -j$(nproc)
    make install

    # Build aria2 with zlib
    cd "$work_dir"
    if [ ! -d "aria2" ]; then
        cp -r "$BUILD_DIR/aria2" aria2
    fi
    cd aria2
    make distclean 2>/dev/null || true
    autoreconf -i

    ./configure \
        --host="$target" \
        --prefix="$prefix" \
        --with-zlib="$prefix" \
        --without-java \
        --disable-bittorrent \
        --disable-metalink \
        --disable-websocket \
        --enable-threads \
        --disable-nls

    make -j$(nproc)
    make install

    local output_abi_dir="$OUTPUT_DIR/$abi"
    mkdir -p "$output_abi_dir"
    cp "$prefix/bin/aria2c" "$output_abi_dir/libaria2c.so"
    chmod +x "$output_abi_dir/libaria2c.so"

    log "aria2 with BT support for $abi built!"
    unset CC CXX AR RANLIB STRIP CFLAGS LDFLAGS
}

# ============================================================
# Copy config files
# ============================================================
copy_configs() {
    log "Copying aria2.conf to assets/engine directories..."
    for abi in "${SELECTED_ABIS[@]}"; do
        local dir="$(pwd)/app/src/main/assets/engine/$abi"
        mkdir -p "$dir"
        cp "$(pwd)/app/src/main/assets/engine/aria2.conf" "$dir/aria2.conf" 2>/dev/null || true
    done
}

# ============================================================
# Print summary
# ============================================================
print_summary() {
    log ""
    log "========================================="
    log "BUILD SUMMARY"
    log "========================================="
    log "aria2 version: $ARIA2_VERSION"
    log "Built ABIs: ${SELECTED_ABIS[*]}"
    log ""
    log "Output binaries:"
    for abi in "${SELECTED_ABIS[@]}"; do
        local binary="$OUTPUT_DIR/$abi/libaria2c.so"
        if [ -f "$binary" ]; then
            local size=$(du -h "$binary" | cut -f1)
            log "  $abi/libaria2c.so ($size)"
        else
            log "  $abi/libaria2c.so - MISSING!"
        fi
    done
    log ""
    log "To upgrade aria2, change ARIA2_VERSION and re-run:"
    log "  ARIA2_VERSION=1.38.0 ./scripts/build-aria2.sh"
}

# ============================================================
# Main
# ============================================================
main() {
    log "aria2 Android Build Script"
    log "Version: $ARIA2_VERSION"
    log "NDK: $NDK_TOOLCHAIN"
    log "Target ABIs: ${SELECTED_ABIS[*]}"
    log ""

    check_prerequisites
    download_aria2

    for abi in "${SELECTED_ABIS[@]}"; do
        build_for_abi "$abi"
    done

    copy_configs
    print_summary

    log ""
    log "Done! Run './gradlew assembleDebug' to build the APK."
}

main "$@"
