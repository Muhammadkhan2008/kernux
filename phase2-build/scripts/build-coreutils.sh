#!/bin/bash
# Build coreutils for Kernux

source ./cross-compile-env.sh

PKG="coreutils"
VERSION="9.1"

echo "Building $PKG-$VERSION..."

# Setup
mkdir -p "$KERNUX_BUILD_ROOT/build/$PKG-$VERSION-"{arm64,armv7,x86_64}

# Download if not exists
if [ ! -d "$KERNUX_BUILD_ROOT/sources/$PKG-$VERSION" ]; then
  echo "Downloading $PKG-$VERSION..."
  cd "$KERNUX_BUILD_ROOT/sources"
  wget -q https://ftp.gnu.org/gnu/$PKG/$PKG-$VERSION.tar.xz
  tar xf $PKG-$VERSION.tar.xz
  cd -
fi

# Build for each architecture
for arch in arm64 armv7 x86_64; do
  echo "Compiling for $arch..."
  build_autotools_package $PKG $VERSION $arch
done

echo "Creating opkg package..."

# Create control file
mkdir -p "$KERNUX_BUILD_ROOT/packages/$PKG/DEBIAN"
cat > "$KERNUX_BUILD_ROOT/packages/$PKG/DEBIAN/control" << EOF
Package: $PKG
Version: $VERSION
Architecture: all
Maintainer: Kernux
Description: GNU core utilities (ls, cat, echo, grep, sed, awk, etc.)
EOF

# Create postinst script
cat > "$KERNUX_BUILD_ROOT/packages/$PKG/DEBIAN/postinst" << EOF
#!/bin/sh
chmod +x $PREFIX/bin/* 2>/dev/null
echo "$PKG installed successfully"
EOF
chmod +x "$KERNUX_BUILD_ROOT/packages/$PKG/DEBIAN/postinst"

# Copy binaries
cp -r "$KERNUX_BUILD_ROOT/build/$PKG-$VERSION-arm64/data/data/com.kernux.app/files/usr" "$KERNUX_BUILD_ROOT/packages/$PKG/"

# Create .opkg (debian package format)
cd "$KERNUX_BUILD_ROOT/packages"
tar czf $PKG-$VERSION.opkg $PKG/DEBIAN $PKG/usr

echo "✓ $PKG-$VERSION.opkg created"
ls -lh $PKG-$VERSION.opkg
