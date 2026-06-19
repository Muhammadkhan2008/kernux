#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/cross-compile-env.sh"

STRACE_VERSION="${1:-6.0}"

echo "════════════════════════════════════════════════════════════════"
echo "Building STRACE ${STRACE_VERSION}"
echo "════════════════════════════════════════════════════════════════"

BUILD_DIR="/tmp/kernux-strace-build-$$"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# Download
if [ ! -f "strace-${STRACE_VERSION}.tar.xz" ]; then
    echo "[*] Downloading STRACE..."
    STRACE_URL="https://github.com/strace/strace/releases/download/v${STRACE_VERSION}/strace-${STRACE_VERSION}.tar.xz"
    wget -q "$STRACE_URL" || { echo "[!] Download failed"; exit 1; }
fi

# Extract
echo "[*] Extracting..."
tar -xJf "strace-${STRACE_VERSION}.tar.xz"
cd "strace-${STRACE_VERSION}"

# Configure
echo "[*] Configuring..."
./configure \
    --host=aarch64-linux-android \
    --prefix="$PREFIX" \
    --disable-shared \
    CFLAGS="$CFLAGS -fPIE -fPIC" \
    LDFLAGS="$LDFLAGS -pie"

# Build
echo "[*] Building..."
make -j8

# Install
echo "[*] Installing..."
make install

# Strip
$CC -s "$PREFIX/bin/strace" 2>/dev/null || true

# Package
echo "[*] Creating package..."
cd "$PREFIX"
PACKAGE_NAME="strace-${STRACE_VERSION}-arm64.tar.gz"
tar -czf "$BUILD_DIR/$PACKAGE_NAME" bin/strace share/man/man1/strace.1* 2>/dev/null || \
    tar -czf "$BUILD_DIR/$PACKAGE_NAME" bin/strace

mkdir -p "$SCRIPT_DIR/../../releases"
cp "$BUILD_DIR/$PACKAGE_NAME" "$SCRIPT_DIR/../../releases/"

echo "[✓] STRACE package created: $PACKAGE_NAME"
echo "[✓] STRACE build complete!"

rm -rf "$BUILD_DIR"
