#!/usr/bin/env bash
# Extracts the SQLite amalgamation (sqlite3.c/.h) from the better-sqlite3 npm package
# into <work_dir>/src/sqlite. Usage: ./fetch_sqlite.sh [work_dir]
set -euo pipefail
WORK="${1:-$HOME/android-native}"
DEST="$WORK/src/sqlite"
[ -f "$DEST/sqlite3.c" ] && exit 0
mkdir -p "$DEST" "$WORK/tmp"
URL=$(curl -sL https://registry.npmjs.org/better-sqlite3/latest | python3 -c 'import sys,json;print(json.load(sys.stdin)["dist"]["tarball"])')
curl -sL -o "$WORK/tmp/better-sqlite3.tgz" "$URL"
tar xzf "$WORK/tmp/better-sqlite3.tgz" -C "$WORK/tmp"
cp "$WORK/tmp/package/deps/sqlite3/sqlite3.c" "$WORK/tmp/package/deps/sqlite3/sqlite3.h" "$DEST/"
echo "sqlite amalgamation at $DEST"
