#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/cross-compile-env.sh"

TCPDUMP_VERSION="${1:-4.99.0}"
LIBPCAP_VERSION="1.10.4"

echo "════════════════════════════════════════════════════════════════"
echo "Building TCPDUMP ${TCPDUMP_VERSION} (with libpcap)"
echo "════════════════════════════════════════════════════════════════"

BUILD_DIR="/tmp/kernux-tcpdump-build-$$"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# Step 1: Build libpcap (dependency)
echo "[*] Building libpcap ${LIBPCAP_VERSION}..."

if [ ! -f "libpcap-${LIBPCAP_VERSION}.tar.gz" ]; then
    LIBPCAP_URL="https://github.com/the-tcpdump-group/libpcap/archive/refs/tags/libpcap-${LIBPCAP_VERSION}.tar.gz"
    wget -q "$LIBPCAP_URL" || { echo "[!] Libpcap download failed"; exit 1; }
fi

tar -xzf "libpcap-${LIBPCAP_VERSION}.tar.gz"
cd "libpcap-libpcap-${LIBPCAP_VERSION}"

./configure \
    --host=aarch64-linux-android \
    --prefix="$PREFIX" \
    --disable-shared \
    --enable-static \
    CFLAGS="$CFLAGS -fPIE -fPIC"

make -j8 && make install
cd ..

echo "[✓] libpcap installed"

# Step 2: Build tcpdump
echo "[*] Building tcpdump ${TCPDUMP_VERSION}..."

if [ ! -f "tcpdump-${TCPDUMP_VERSION}.tar.gz" ]; then
    TCPDUMP_URL="https://github.com/the-tcpdump-group/tcpdump/archive/refs/tags/tcpdump-${TCPDUMP_VERSION}.tar.gz"
    wget -q "$TCPDUMP_URL" || { echo "[!] Tcpdump download failed"; exit 1; }
fi

tar -xzf "tcpdump-${TCPDUMP_VERSION}.tar.gz"
cd "tcpdump-tcpdump-${TCPDUMP_VERSION}"

./configure \
    --host=aarch64-linux-android \
    --prefix="$PREFIX" \
    LDFLAGS="-L$PREFIX/lib" \
    CPPFLAGS="-I$PREFIX/include" \
    CFLAGS="$CFLAGS -fPIE -fPIC"

make -j8 && make install

# Strip
$CC -s "$PREFIX/bin/tcpdump" 2>/dev/null || true

# Package
echo "[*] Creating package..."
cd "$PREFIX"
PACKAGE_NAME="tcpdump-${TCPDUMP_VERSION}-arm64.tar.gz"
tar -czf "$BUILD_DIR/$PACKAGE_NAME" bin/tcpdump lib/libpcap* 2>/dev/null || \
    tar -czf "$BUILD_DIR/$PACKAGE_NAME" bin/tcpdump

mkdir -p "$SCRIPT_DIR/../../releases"
cp "$BUILD_DIR/$PACKAGE_NAME" "$SCRIPT_DIR/../../releases/"

echo "[✓] TCPDUMP package created: $PACKAGE_NAME"
echo "[✓] TCPDUMP build complete!"

rm -rf "$BUILD_DIR"
