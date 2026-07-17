#!/usr/bin/env bash
# scripts/package-macos.sh
# Builds the fat JAR, assembles the macOS .app bundle, and zips it to dist/.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_NAME="EA FC 26 Discord Stats"
APP_BUNDLE="$APP_NAME.app"
DIST_DIR="$PROJECT_DIR/dist"
BUNDLE_TEMPLATE="$PROJECT_DIR/app-bundle"

echo "==> Building fat JAR (bootJar)..."
cd "$PROJECT_DIR"
./gradlew clean bootJar -x test

JAR_SRC=$(ls "$PROJECT_DIR/build/libs/"*.jar | head -1)
echo "    JAR: $JAR_SRC"

echo "==> Assembling $APP_BUNDLE..."
TMP_APP="$DIST_DIR/$APP_BUNDLE"
rm -rf "$TMP_APP"
mkdir -p "$TMP_APP/Contents/MacOS"
mkdir -p "$TMP_APP/Contents/Resources"

# Copy Info.plist and launcher from template
cp "$BUNDLE_TEMPLATE/Contents/Info.plist" "$TMP_APP/Contents/"
cp "$BUNDLE_TEMPLATE/Contents/MacOS/launcher" "$TMP_APP/Contents/MacOS/"
chmod +x "$TMP_APP/Contents/MacOS/launcher"

# Copy the fat JAR
cp "$JAR_SRC" "$TMP_APP/Contents/Resources/eafc26-discord-stats.jar"

echo "==> Ad-hoc codesign (no Apple Developer ID required)..."
if command -v codesign &>/dev/null; then
    codesign --force --deep --sign - "$TMP_APP" || echo "    (codesign skipped — not on macOS or no identity)"
else
    echo "    (codesign not available on this platform — skip)"
fi

echo "==> Creating ZIP..."
mkdir -p "$DIST_DIR"
ZIP_FILE="$DIST_DIR/EA-FC-26-Discord-Stats-macOS.zip"
rm -f "$ZIP_FILE"
(cd "$DIST_DIR" && zip -r "$ZIP_FILE" "$APP_BUNDLE")

echo ""
echo "==> Done!"
echo "    Bundle : $TMP_APP"
echo "    ZIP    : $ZIP_FILE"
echo ""
echo "To install: unzip the ZIP and drag '${APP_BUNDLE}' to /Applications."
echo "Double-click the app to launch."
