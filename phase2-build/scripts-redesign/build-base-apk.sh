#!/bin/bash
# Build KERNUX BASE APK (100 MB, no packages)
# This is the small app users download

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)/../.."
cd "$PROJECT_DIR"

echo "═════════════════════════════════════════════════════════════"
echo "  KERNUX PHASE 2 REDESIGN - BUILD BASE APK (100 MB)"
echo "═════════════════════════════════════════════════════════════"
echo ""

echo "[1/4] Cleaning previous builds..."
./gradlew clean

echo ""
echo "[2/4] Building base APK (no embedded packages)..."
./gradlew assembleDebug

echo ""
echo "[3/4] APK created:"
ls -lh app/build/outputs/apk/debug/app-debug.apk

echo ""
echo "[4/4] Ready for deployment:"
echo "  • APK: app/build/outputs/apk/debug/app-debug.apk"
echo "  • Size: Check above"
echo "  • Next: Upload to Play Store / GitHub Releases"
echo ""
echo "═════════════════════════════════════════════════════════════"
echo "BASE APK READY!"
echo "═════════════════════════════════════════════════════════════"
