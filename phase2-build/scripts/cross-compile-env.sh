#!/bin/bash
# Kernux Phase 2 - Cross-Compilation Environment Setup

export KERNUX_BUILD_ROOT="$(cd ../..; pwd)/phase2-build"
export NDK_HOME="${NDK_HOME:-$HOME/android-ndk-r25b}"
export PREFIX="/data/data/com.kernux.app/files/usr"

# Architecture configurations
declare -A ARCH_CONFIG=(
  [arm64]="aarch64-linux-android21"
  [armv7]="armv7a-linux-androideabi21"
  [x86_64]="x86_64-linux-android21"
)

# Toolchain setup
setup_toolchain() {
  local arch=$1
  local prefix="${ARCH_CONFIG[$arch]}"
  
  export TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
  export CC="$TOOLCHAIN/bin/${prefix}-clang"
  export CXX="$TOOLCHAIN/bin/${prefix}-clang++"
  export AR="$TOOLCHAIN/bin/llvm-ar"
  export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
  export LD="$TOOLCHAIN/bin/llvm-ld"
  
  export CFLAGS="-O2 -fPIC"
  export CXXFLAGS="-O2 -fPIC"
  export LDFLAGS="-L$PREFIX/lib"
  export CPPFLAGS="-I$PREFIX/include"
}

# Functions for package compilation
build_autotools_package() {
  local pkg=$1
  local version=$2
  local arch=$3
  
  setup_toolchain $arch
  
  cd "$KERNUX_BUILD_ROOT/sources/$pkg-$version"
  
  ./configure \
    --prefix=$PREFIX \
    --host=${ARCH_CONFIG[$arch]} \
    --enable-static \
    --disable-shared \
    --disable-nls
    
  make -j4
  make DESTDIR="$KERNUX_BUILD_ROOT/build/$pkg-$version-$arch" install
}

build_cmake_package() {
  local pkg=$1
  local version=$2
  local arch=$3
  
  setup_toolchain $arch
  
  cd "$KERNUX_BUILD_ROOT/sources/$pkg-$version"
  mkdir -p build
  cd build
  
  cmake .. \
    -DCMAKE_INSTALL_PREFIX=$PREFIX \
    -DCMAKE_C_COMPILER=$CC \
    -DCMAKE_CXX_COMPILER=$CXX \
    -DBUILD_SHARED_LIBS=OFF
    
  make -j4
  make DESTDIR="$KERNUX_BUILD_ROOT/build/$pkg-$version-$arch" install
}

echo "Kernux Phase 2 - Cross-Compilation Environment Ready"
echo "NDK: $NDK_HOME"
echo "Prefix: $PREFIX"
echo "Available architectures: arm64, armv7, x86_64"
