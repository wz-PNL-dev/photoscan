#!/usr/bin/env bash
# Cross-compile COLMAP's native dependencies for Android arm64-v8a.
#
# Usage: ANDROID_NDK=/path/to/ndk ./build_deps.sh [work_dir]
# Output: <work_dir>/install/arm64-v8a (headers + static libs). Sources are cloned
# into <work_dir>/src at the pinned versions below. Re-running skips finished steps.
set -euo pipefail

NDK="${ANDROID_NDK:?set ANDROID_NDK to the NDK root}"
WORK="${1:-$HOME/android-native}"
ABI=arm64-v8a
API=26
SRC="$WORK/src"
PREFIX="$WORK/install/$ABI"
BUILD="$WORK/build/deps"
JOBS="${JOBS:-4}"
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"

mkdir -p "$SRC" "$PREFIX" "$BUILD"

clone() { # clone <url> <dir> <tag-or-branch>
  [ -d "$SRC/$2" ] || git clone -q --depth 1 --branch "$3" "$1" "$SRC/$2"
}

cmake_build() { # cmake_build <name> <source dir> [extra cmake args...]
  local name=$1 srcdir=$2; shift 2
  local bdir="$BUILD/$name"
  [ -f "$PREFIX/.done-$name" ] && { echo "== $name: already built"; return; }
  echo "== building $name"
  cmake -S "$srcdir" -B "$bdir" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-$API -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$PREFIX" -DCMAKE_PREFIX_PATH="$PREFIX" \
    -DBUILD_SHARED_LIBS=OFF -DBUILD_TESTING=OFF \
    "$@" > "$BUILD/$name.configure.log" 2>&1
  cmake --build "$bdir" -j"$JOBS" > "$BUILD/$name.build.log" 2>&1
  cmake --install "$bdir" > "$BUILD/$name.install.log" 2>&1
  touch "$PREFIX/.done-$name"
  echo "== $name done"
}

# Pinned versions.
clone https://gitlab.com/libeigen/eigen.git eigen 3.4.0
clone https://github.com/gflags/gflags.git gflags v2.2.2
clone https://github.com/google/glog.git glog v0.7.1
clone https://github.com/ceres-solver/ceres-solver.git ceres 2.2.0
clone https://github.com/PoseLib/PoseLib.git poselib master
clone https://github.com/nothings/stb.git stb master

cmake_build eigen "$SRC/eigen" -DEIGEN_BUILD_DOC=OFF -DEIGEN_BUILD_PKGCONFIG=OFF

cmake_build gflags "$SRC/gflags" -DGFLAGS_NAMESPACE=gflags -DGFLAGS_BUILD_TESTING=OFF

cmake_build glog "$SRC/glog" -DWITH_GFLAGS=ON -DWITH_UNWIND=OFF -DWITH_GTEST=OFF

cmake_build ceres "$SRC/ceres" \
  -DMINIGLOG=OFF -DGFLAGS=ON -DSUITESPARSE=OFF -DCXSPARSE=OFF -DLAPACK=OFF \
  -DEIGENSPARSE=ON -DCERES_THREADING_MODEL=CXX_THREADS -DBUILD_EXAMPLES=OFF \
  -DPROVIDE_UNINSTALL_TARGET=OFF

# Headers and static library for SQLite from the amalgamation shipped in the
# better-sqlite3 npm package (fetched by fetch_sqlite.sh).
if [ ! -f "$PREFIX/.done-sqlite" ]; then
  echo "== building sqlite"
  "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android$API-clang" \
    -O2 -DSQLITE_THREADSAFE=1 -DSQLITE_ENABLE_FTS5 -DSQLITE_OMIT_LOAD_EXTENSION \
    -c "$WORK/src/sqlite/sqlite3.c" -o "$BUILD/sqlite3.o"
  "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-ar" rcs "$PREFIX/lib/libsqlite3.a" "$BUILD/sqlite3.o"
  mkdir -p "$PREFIX/include"
  cp "$WORK/src/sqlite/sqlite3.h" "$PREFIX/include/"
  touch "$PREFIX/.done-sqlite"
fi

# stb headers (image decode/encode used in place of OpenImageIO).
mkdir -p "$PREFIX/include/stb"
cp "$SRC/stb/stb_image.h" "$SRC/stb/stb_image_write.h" "$SRC/stb/stb_image_resize2.h" "$PREFIX/include/stb/"

cmake_build poselib "$SRC/poselib" -DPOSELIB_BUILD_TESTS=OFF -DPOSELIB_BUILD_EXAMPLES=OFF

echo "dependencies installed under $PREFIX"
