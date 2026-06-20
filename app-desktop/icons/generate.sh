#!/usr/bin/env bash
#
# Regenerate the platform icon set from the source artwork in ./source
# (AppIcon.icns + AppIcon<size>.png, the macOS icon the owner designed).
#
# Outputs (committed, referenced by build.gradle.kts and the app at runtime):
#   VaultMesh.icns                 -> macOS installer icon
#   VaultMesh.ico                  -> Windows installer icon
#   VaultMesh.png                  -> Linux installer icon
#   ../src/main/resources/icon.png -> in-app window / dock icon
#
#   ./generate.sh
#
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

SRC="source"
[ -f "$SRC/AppIcon.icns" ] || { echo "Missing $SRC/AppIcon.icns" >&2; exit 1; }

echo "==> macOS .icns"
cp "$SRC/AppIcon.icns" VaultMesh.icns

echo "==> Windows .ico (PNG-embedded, Vista+)"
python3 make_ico.py VaultMesh.ico \
  "$SRC/AppIcon16.png" "$SRC/AppIcon32.png" "$SRC/AppIcon64.png" "$SRC/AppIcon128.png" "$SRC/AppIcon256.png"

echo "==> Linux + in-app runtime PNGs"
cp "$SRC/AppIcon512.png" VaultMesh.png
cp "$SRC/AppIcon512.png" ../src/main/resources/icon.png

echo "==> Done:"
ls -la VaultMesh.icns VaultMesh.ico VaultMesh.png ../src/main/resources/icon.png
