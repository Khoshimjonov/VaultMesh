#!/usr/bin/env bash
#
# Build the VaultMesh "install-and-use" package for the current OS.
#   macOS -> .dmg   |   Linux -> .deb
#
# The build bundles a full Java runtime AND the rclone engine, so the resulting
# installer needs nothing preinstalled on the target machine. Requires a JDK 17+
# to run the build itself.
#
# Usage:  ./scripts/package.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(dirname "$SCRIPT_DIR")"

echo "==> VaultMesh packaging"
echo "    Project: $ROOT"

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: Java 17+ is required to build, but 'java' was not found on PATH." >&2
  echo "       Install Temurin/OpenJDK 17+ and try again." >&2
  exit 1
fi

echo "==> Building native distribution (bundles runtime + rclone engine)…"
"$ROOT/gradlew" -p "$ROOT" :app-desktop:packageDistributionForCurrentOS

OUT="$ROOT/app-desktop/build/compose/binaries/main"
echo ""
echo "==> Done. Installer(s):"
INSTALLERS="$(find "$OUT" -maxdepth 2 -type f \( -name '*.dmg' -o -name '*.deb' -o -name '*.rpm' \) 2>/dev/null || true)"
if [ -n "$INSTALLERS" ]; then
  echo "$INSTALLERS" | sed 's/^/    /'
else
  echo "    (see $OUT)"
fi

# Open the output folder (best-effort; ignore failures on headless machines).
if command -v open >/dev/null 2>&1; then
  open "$OUT/dmg" 2>/dev/null || open "$OUT" 2>/dev/null || true
elif command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$OUT/deb" 2>/dev/null || xdg-open "$OUT" 2>/dev/null || true
fi

echo ""
echo "Tip (macOS): the app is unsigned — first launch may need right-click > Open."
