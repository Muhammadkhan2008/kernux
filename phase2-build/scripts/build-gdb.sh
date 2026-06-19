#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/cross-compile-env.sh"

GDB_VERSION="${1:-13.0}"

echo "════════════════════════════════════════════════════════════════"
echo "Building GDB (GNU Debugger) ${GDB_VERSION}"
echo "════════════════════════════════════════════════════════════════"

BUILD_DIR="/tmp/kernux-gdb-build-$$"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# Download
if [ ! -f "gdb-${GDB_VERSION}.tar.xz" ]; then
    echo "[*] Downloading GDB ${GDB_VERSION}..."
    GDB_URL="https://ftpmirror.gnu.org/gdb/gdb-${GDB_VERSION}.tar.xz"
    wget -q "$GDB_URL" || { echo "[!] Download failed"; exit 1; }
fi

# Extract
echo "[*] Extracting..."
tar -xJf "gdb-${GDB_VERSION}.tar.xz"
cd "gdb-${GDB_VERSION}"

# Configure
echo "[*] Configuring for ARM64 Android..."
./configure \
    --host=aarch64-linux-android \
    --prefix="$PREFIX" \
    --disable-shared \
    --enable-gdbserver=yes \
    CFLAGS="$CFLAGS -fPIE -fPIC" \
    LDFLAGS="$LDFLAGS -pie"

# Build (GDB takes longer)
echo "[*] Building (this may take 10-15 minutes)..."
make -j8

# Install
echo "[*] Installing..."
make install

# Strip both gdb and gdbserver
$CC -s "$PREFIX/bin/gdb" 2>/dev/null || true
$CC -s "$PREFIX/bin/gdbserver" 2>/dev/null || true

# Package
echo "[*] Creating package..."
cd "$PREFIX"
PACKAGE_NAME="gdb-${GDB_VERSION}-arm64.tar.gz"
tar -czf "$BUILD_DIR/$PACKAGE_NAME" bin/gdb bin/gdbserver lib/ share/gdb 2>/dev/null || \
    tar -czf "$BUILD_DIR/$PACKAGE_NAME" bin/gdb bin/gdbserver

mkdir -p "$SCRIPT_DIR/../../releases"
cp "$BUILD_DIR/$PACKAGE_NAME" "$SCRIPT_DIR/../../releases/"

echo "[✓] GDB package created: $PACKAGE_NAME"
echo "[✓] GDB build complete!"
echo ""
echo "Usage on Kernux:"
echo "  pkg install gdb"
echo "  gdb ./binary"
echo "  gdbserver :5005 ./binary"

rm -rf "$BUILD_DIR"
