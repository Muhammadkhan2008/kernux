#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/cross-compile-env.sh"

NETCAT_VERSION="${1:-1.10-41.1}"

echo "════════════════════════════════════════════════════════════════"
echo "Building NETCAT (OpenBSD) ${NETCAT_VERSION}"
echo "════════════════════════════════════════════════════════════════"

BUILD_DIR="/tmp/kernux-netcat-build-$$"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# Download OpenBSD netcat source
if [ ! -f "netcat-openbsd-${NETCAT_VERSION}.tar.gz" ]; then
    echo "[*] Downloading netcat-openbsd..."
    # Using Debian mirror for netcat-openbsd source
    NETCAT_URL="https://salsa.debian.org/debian/netcat-openbsd/-/archive/debian/${NETCAT_VERSION}/netcat-openbsd-debian-${NETCAT_VERSION}.tar.gz"
    wget -q "$NETCAT_URL" -O "netcat-openbsd-${NETCAT_VERSION}.tar.gz" || \
        { echo "[!] Download failed, trying alternative..."; exit 1; }
fi

# Extract
echo "[*] Extracting..."
tar -xzf "netcat-openbsd-${NETCAT_VERSION}.tar.gz"
cd "netcat-openbsd-debian-${NETCAT_VERSION}"

# Build (OpenBSD netcat is simple - just compile the sources)
echo "[*] Building..."
mkdir -p "$PREFIX/bin"

# Compile all source files
$CC $CFLAGS -fPIE -fPIC \
    -o nc \
    netcat.c atomicio.c socks.c \
    $LDFLAGS -pie 2>&1 | head -5 || true

# Check if binary was created
if [ ! -f nc ]; then
    echo "[!] Build failed, trying alternate method..."
    # Try using make if available
    [ -f Makefile ] && make LDFLAGS="$LDFLAGS -pie" || true
fi

# Install
if [ -f nc ]; then
    cp nc "$PREFIX/bin/netcat"
    chmod +x "$PREFIX/bin/netcat"
    ln -sf netcat "$PREFIX/bin/nc" 2>/dev/null || true
    
    # Strip
    $CC -s "$PREFIX/bin/netcat" 2>/dev/null || true
else
    echo "[!] Warning: netcat binary not found"
fi

# Package
echo "[*] Creating package..."
cd "$PREFIX"
PACKAGE_NAME="netcat-${NETCAT_VERSION}-arm64.tar.gz"
tar -czf "$BUILD_DIR/$PACKAGE_NAME" bin/netcat bin/nc 2>/dev/null || \
    tar -czf "$BUILD_DIR/$PACKAGE_NAME" bin/netcat

mkdir -p "$SCRIPT_DIR/../../releases"
cp "$BUILD_DIR/$PACKAGE_NAME" "$SCRIPT_DIR/../../releases/"

echo "[✓] NETCAT package created: $PACKAGE_NAME"
echo "[✓] NETCAT build complete!"
echo ""
echo "Usage on Kernux:"
echo "  pkg install netcat"
echo "  nc -l -p 4444          (listen mode)"
echo "  nc target.com 22       (connect & banner grab)"
echo "  nc -nv target 1-1000   (port scan)"

rm -rf "$BUILD_DIR"
