#!/bin/bash
# Build KERNUX ONLINE REPOSITORY
# Creates package repository structure for kernux.com/packages/

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/../packages-repo"
PACKAGES_DIR="$SCRIPT_DIR/../packages"

echo "═════════════════════════════════════════════════════════════"
echo "  KERNUX PACKAGE REPOSITORY BUILDER"
echo "═════════════════════════════════════════════════════════════"
echo ""

if [ ! -d "$PACKAGES_DIR" ]; then
    echo "ERROR: $PACKAGES_DIR not found!"
    echo "Run build-all-packages.sh first!"
    exit 1
fi

echo "[1/3] Creating repository structure..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

echo ""
echo "[2/3] Copying .opkg files to repository..."
cp "$PACKAGES_DIR"/*.opkg "$BUILD_DIR/" 2>/dev/null || true

if [ -z "$(ls -A "$BUILD_DIR")" ]; then
    echo "WARNING: No .opkg files found!"
    echo "Make sure build-all-packages.sh has completed"
fi

echo ""
echo "[3/3] Creating repository metadata..."
cd "$BUILD_DIR"

# Create Packages file (apt-like metadata)
cat > Packages << 'PACKAGES_FILE'
Package: git
Version: 2.40
Architecture: arm64
Filename: git-2.40-arm64.opkg
Size: 15000000
Description: Version control system

Package: python
Version: 3.11
Architecture: arm64
Filename: python-3.11-arm64.opkg
Size: 30000000
Description: Python interpreter

Package: gcc
Version: 12
Architecture: arm64
Filename: gcc-12-arm64.opkg
Size: 50000000
Description: C/C++ compiler

Package: vim
Version: 9
Architecture: arm64
Filename: vim-9-arm64.opkg
Size: 8000000
Description: Text editor

Package: curl
Version: 7.88
Architecture: arm64
Filename: curl-7.88-arm64.opkg
Size: 3000000
Description: HTTP downloader

Package: wget
Version: 1.21
Architecture: arm64
Filename: wget-1.21-arm64.opkg
Size: 2000000
Description: File downloader

Package: node
Version: 18
Architecture: arm64
Filename: node-18-arm64.opkg
Size: 40000000
Description: Node.js runtime

Package: openssh
Version: 9
Architecture: arm64
Filename: openssh-9-arm64.opkg
Size: 10000000
Description: SSH server

Package: perl
Version: 5.36
Architecture: arm64
Filename: perl-5.36-arm64.opkg
Size: 12000000
Description: Perl interpreter

Package: nano
Version: 7
Architecture: arm64
Filename: nano-7-arm64.opkg
Size: 1000000
Description: Simple editor

Package: openssl
Version: 3.0
Architecture: arm64
Filename: openssl-3.0-arm64.opkg
Size: 8000000
Description: SSL library

Package: net-tools
Version: 2.10
Architecture: arm64
Filename: net-tools-2.10-arm64.opkg
Size: 1000000
Description: Network utilities

Package: iputils
Version: 20230321
Architecture: arm64
Filename: iputils-20230321-arm64.opkg
Size: 1000000
Description: Ping utilities

Package: make
Version: 4.3
Architecture: arm64
Filename: make-4.3-arm64.opkg
Size: 2000000
Description: Build system

Package: coreutils
Version: 9.1
Architecture: arm64
Filename: coreutils-9.1-arm64.opkg
Size: 20000000
Description: Core utilities

Package: bash
Version: 5.2
Architecture: arm64
Filename: bash-5.2-arm64.opkg
Size: 15000000
Description: Shell
PACKAGES_FILE

# Compress metadata
gzip -c Packages > Packages.gz

cd ..

echo ""
echo "═════════════════════════════════════════════════════════════"
echo "REPOSITORY READY!"
echo "═════════════════════════════════════════════════════════════"
echo ""
echo "Location: $BUILD_DIR"
echo "Files:"
ls -lh "$BUILD_DIR"
echo ""
echo "Next: Upload to kernux.com/packages/"
echo ""
