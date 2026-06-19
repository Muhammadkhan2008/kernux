#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/cross-compile-env.sh"

JOHN_VERSION="${1:-1.9.0-jumbo-1}"

echo "════════════════════════════════════════════════════════════════"
echo "Building JOHN THE RIPPER ${JOHN_VERSION}"
echo "════════════════════════════════════════════════════════════════"

BUILD_DIR="/tmp/kernux-john-build-$$"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# Download
if [ ! -f "john-${JOHN_VERSION}.tar.gz" ]; then
    echo "[*] Downloading John..."
    JOHN_URL="https://github.com/openwall/john/archive/refs/tags/${JOHN_VERSION}.tar.gz"
    wget -q "$JOHN_URL" || { echo "[!] Download failed"; exit 1; }
fi

# Extract
echo "[*] Extracting..."
tar -xzf "john-${JOHN_VERSION}.tar.gz"
cd "john-${JOHN_VERSION}/src"

# Configure
echo "[*] Configuring..."
./configure \
    --host=aarch64-linux-android \
    --prefix="$PREFIX" \
    --enable-simd=neon \
    CFLAGS="$CFLAGS -fPIE -fPIC" \
    LDFLAGS="$LDFLAGS"

# Build
echo "[*] Building with -j8..."
make -j8

# Install
echo "[*] Installing..."
make install

# Create symlinks for convenience
ln -sf john "$PREFIX/bin/unshadow" 2>/dev/null || true
ln -sf john "$PREFIX/bin/unafs" 2>/dev/null || true
ln -sf john "$PREFIX/bin/unique" 2>/dev/null || true

# Strip
$CC -s "$PREFIX/bin/john" 2>/dev/null || true

# Package
echo "[*] Creating package..."
cd "$PREFIX"
PACKAGE_NAME="john-${JOHN_VERSION}-arm64.tar.gz"
tar -czf "$BUILD_DIR/$PACKAGE_NAME" bin/ lib/ share/john 2>/dev/null || \
    tar -czf "$BUILD_DIR/$PACKAGE_NAME" bin/

mkdir -p "$SCRIPT_DIR/../../releases"
cp "$BUILD_DIR/$PACKAGE_NAME" "$SCRIPT_DIR/../../releases/"

echo "[✓] JOHN package created: $PACKAGE_NAME"
echo "[✓] JOHN build complete!"

rm -rf "$BUILD_DIR"
