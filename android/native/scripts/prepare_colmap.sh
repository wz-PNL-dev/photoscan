#!/usr/bin/env bash
# Copies COLMAP and applies the Android changes:
#  - sensor/bitmap.cc is replaced with an OpenImageIO-free version (stb_image based).
#  - OpenImageIO is removed from the CMake targets that linked it, and
#    util/oiio_utils is dropped with it.
# Usage: ./prepare_colmap.sh <colmap_source_dir> <out_dir>
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:?colmap source dir}"
OUT="${2:?output dir}"

rm -rf "$OUT"
cp -r "$SRC" "$OUT"

cp "$HERE/patches/sensor/bitmap.cc" "$OUT/src/colmap/sensor/bitmap.cc"

# Drop OpenImageIO from the CMake lists (keep the rest of each file).
for f in "$OUT/src/colmap/sensor/CMakeLists.txt" "$OUT/src/colmap/util/CMakeLists.txt"; do
  sed -i '/OpenImageIO::OpenImageIO/d' "$f"
done
sed -i '/oiio_utils/d' "$OUT/src/colmap/util/CMakeLists.txt"
rm -f "$OUT/src/colmap/util/oiio_utils.cc" "$OUT/src/colmap/util/oiio_utils.h"

# The LoMa extractor includes OpenImageIO unconditionally; drop only those includes.
sed -i '/#include <OpenImageIO/d' "$OUT/src/colmap/feature/loma.cc"

echo "patched COLMAP at $OUT"
