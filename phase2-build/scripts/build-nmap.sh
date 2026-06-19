#!/bin/bash
set -e

# Source Android NDK toolchain setup
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/cross-compile-env.sh"

NMAP_VERSION="${1:-7.94}"
NMAP_URL="https://github.com/nmap/nmap/releases/download/nmap-${NMAP_VERSION}/nmap-${NMAP_VERSION}.tar.bz2"

echo "════════════════════════════════════════════════════════════════"
echo "Building NMAP ${NMAP_VERSION} for Android ARM64"
echo "════════════════════════════════════════════════════════════════"

# Create build directory
BUILD_DIR="/tmp/kernux-nmap-build-$$"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# Download source
if [ ! -f "nmap-${NMAP_VERSION}.tar.bz2" ]; then
    echo "[*] Downloading nmap ${NMAP_VERSION}..."
    wget -q "$NMAP_URL" || { echo "[!] Download failed"; exit 1; }
fi

# Extract
echo "[*] Extracting..."
tar -xjf "nmap-${NMAP_VERSION}.tar.bz2"
cd "nmap-${NMAP_VERSION}"

# Configure for Android cross-compilation
echo "[*] Configuring for ARM64 Android..."
./configure \
    --host=aarch64-linux-android \
    --prefix="$PREFIX" \
    --without-zenmap \
    --without-nping \
    --without-ncat \
    --enable-static \
    --disable-shared \
    CFLAGS="$CFLAGS -fPIE -fPIC -DANDROID" \
    LDFLAGS="$LDFLAGS -pie"

# Build
echo "[*] Building with -j8..."
make -j8

# Install
echo "[*] Installing..."
make install

# Strip binary
echo "[*] Stripping binary..."
$CC -s "$PREFIX/bin/nmap" 2>/dev/null || true

# Create package
echo "[*] Creating .tar.gz package..."
cd "$PREFIX"
PACKAGE_NAME="nmap-${NMAP_VERSION}-arm64.tar.gz"
tar -czf "$BUILD_DIR/$PACKAGE_NAME" bin/nmap 2>/dev/null || true

# Move to releases
mkdir -p "$SCRIPT_DIR/../../releases"
cp "$BUILD_DIR/$PACKAGE_NAME" "$SCRIPT_DIR/../../releases/"

echo "[✓] Package created: $PACKAGE_NAME"
echo "[✓] NMAP build complete!"

# Cleanup
rm -rf "$BUILD_DIR"
