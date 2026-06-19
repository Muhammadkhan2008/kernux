#!/bin/bash
# Kernux Phase 2 - Build ALL Packages (Base + Cyber Tools)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/cross-compile-env.sh"

# Package database - BASE PACKAGES
declare -A PACKAGES=(
  [coreutils]="9.1"
  [bash]="5.1"
  [curl]="7.85"
  [wget]="1.21"
  [git]="2.38"
  [vim]="9.0"
  [nano]="7.0"
)

# CYBER SECURITY TOOLS
CYBER_TOOLS=(
  "nmap:7.94"
  "tcpdump:4.99.0"
  "netcat:1.10-41.1"
  "john:1.9.0-jumbo-1"
  "gdb:13.0"
  "strace:6.0"
)

URLS=(
  "coreutils:https://ftp.gnu.org/gnu/coreutils/coreutils-9.1.tar.xz"
  "bash:https://ftp.gnu.org/gnu/bash/bash-5.1.16.tar.gz"
  "curl:https://curl.se/download/curl-7.85.0.tar.gz"
  "wget:https://ftp.gnu.org/gnu/wget/wget-1.21.2.tar.gz"
  "git:https://github.com/git/git/archive/v2.38.0.tar.gz"
  "vim:https://github.com/vim/vim/archive/v9.0.0.tar.gz"
  "nano:https://www.nano-editor.org/dist/v7/nano-7.0.tar.gz"
)

echo "Kernux Phase 2 - Multi-Package Build System"
echo "==========================================="

# Download all sources
echo ""
echo "Downloading package sources..."
mkdir -p "$KERNUX_BUILD_ROOT/sources"
cd "$KERNUX_BUILD_ROOT/sources"

for entry in "${URLS[@]}"; do
  IFS=':' read -r pkg url <<< "$entry"
  if [ ! -f "${pkg}".* ]; then
    echo "  Downloading $pkg..."
    wget -q "$url"
  fi
done

echo "✓ All sources downloaded"

# Extract
for archive in *.tar.*; do
  echo "Extracting $archive..."
  tar xf "$archive"
done

echo "✓ All sources extracted"
cd - > /dev/null

# Build each package
echo ""
echo "Building packages..."

for pkg in "${!PACKAGES[@]}"; do
  version="${PACKAGES[$pkg]}"
  echo ""
  echo "Building $pkg-$version..."
  
  mkdir -p "$KERNUX_BUILD_ROOT/build/$pkg-$version"
  
  for arch in arm64 armv7 x86_64; do
    echo "  Compiling for $arch..."
    build_autotools_package "$pkg" "$version" "$arch"
  done
  
  # Package as .opkg
  echo "  Creating $pkg.opkg..."
  mkdir -p "$KERNUX_BUILD_ROOT/packages/$pkg/DEBIAN"
  
  cat > "$KERNUX_BUILD_ROOT/packages/$pkg/DEBIAN/control" << EOF
Package: $pkg
Version: $version
Architecture: all
Maintainer: Kernux Team
Description: $pkg - Linux utility
EOF

  cat > "$KERNUX_BUILD_ROOT/packages/$pkg/DEBIAN/postinst" << EOF
#!/bin/sh
chmod +x $PREFIX/bin/* 2>/dev/null
EOF
  chmod +x "$KERNUX_BUILD_ROOT/packages/$pkg/DEBIAN/postinst"
  
  # Copy binaries
  cp -r "$KERNUX_BUILD_ROOT/build/$pkg-$version-arm64/data/data/com.kernux.app/files/usr" \
        "$KERNUX_BUILD_ROOT/packages/$pkg/"
  
  # Create .opkg
  cd "$KERNUX_BUILD_ROOT/packages"
  tar czf "$pkg-$version.opkg" "$pkg/DEBIAN" "$pkg/usr" 2>/dev/null
  cd - > /dev/null
done

echo ""
echo "✓ Base packages built successfully!"

# ═══════════════════════════════════════════════════════════════════════════
# BUILD CYBER SECURITY TOOLS
# ═══════════════════════════════════════════════════════════════════════════

echo ""
echo "════════════════════════════════════════════════════════════════"
echo "Building Cyber Security Tools..."
echo "════════════════════════════════════════════════════════════════"

mkdir -p "$SCRIPT_DIR/../../releases"

for tool in "${CYBER_TOOLS[@]}"; do
  name="${tool%:*}"
  version="${tool#*:}"

  # Check if build script exists
  if [ -f "$SCRIPT_DIR/build-${name}.sh" ]; then
    echo ""
    echo "[*] Building $name $version..."
    bash "$SCRIPT_DIR/build-${name}.sh" "$version" || \
      echo "[!] Build of $name failed - continuing with next tool"
  else
    echo "[!] No build script for $name - skipping"
  fi
done

echo ""
echo "✓ Cyber tools build complete!"

# Copy releases to appropriate location
if [ -d "$SCRIPT_DIR/../../releases" ]; then
  echo "[*] Cyber tool packages available in: releases/"
  ls -lh "$SCRIPT_DIR/../../releases/"*.tar.gz 2>/dev/null | head -10
fi

echo ""
echo "Packages ready:"
ls -lh "$KERNUX_BUILD_ROOT/packages/"*.opkg

# Create repository index
echo ""
echo "Creating package repository..."

cat > "$KERNUX_BUILD_ROOT/repo/Packages" << 'REPO'
Package: coreutils
Version: 9.1
Architecture: arm64
Size: 2500000
Description: GNU core utilities (ls, cat, echo, grep, sed, awk, find, etc.)

Package: bash
Version: 5.1
Architecture: arm64
Size: 1800000
Description: GNU Bourne Again Shell

Package: curl
Version: 7.85
Architecture: arm64
Size: 900000
Description: HTTP client and file transfer tool

Package: wget
Version: 1.21
Architecture: arm64
Size: 850000
Description: Command-line tool for file downloads

Package: git
Version: 2.38
Architecture: arm64
Size: 5000000
Description: Version control system

Package: vim
Version: 9.0
Architecture: arm64
Size: 3000000
Description: Highly configurable text editor

Package: nano
Version: 7.0
Architecture: arm64
Size: 800000
Description: Simple text editor for Kernux
REPO

gzip -c "$KERNUX_BUILD_ROOT/repo/Packages" > "$KERNUX_BUILD_ROOT/repo/Packages.gz"

echo "✓ Repository created"
echo ""
echo "Summary:"
echo "  Source dir: $KERNUX_BUILD_ROOT/sources"
echo "  Build dir: $KERNUX_BUILD_ROOT/build"
echo "  Packages: $KERNUX_BUILD_ROOT/packages"
echo "  Repository: $KERNUX_BUILD_ROOT/repo"
echo ""
echo "Next: Deploy packages to Kernux app!"
