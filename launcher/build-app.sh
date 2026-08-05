#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_DIR="$PROJECT_DIR/dist"
APP_DIR="$DIST_DIR/EA FC Stats.app/Contents"

echo "=== Building EA FC Stats.app ==="

# Compile Swift launcher
echo "Compiling SwiftUI launcher…"
swiftc -parse-as-library \
    -framework AppKit \
    -framework SwiftUI \
    -O \
    -o "$SCRIPT_DIR/EAFCStatsLauncher" \
    "$SCRIPT_DIR/EAFCStatsLauncher.swift"

# Create bundle structure
rm -rf "$DIST_DIR/EA FC Stats.app"
mkdir -p "$APP_DIR/MacOS" "$APP_DIR/Resources"

# Info.plist
cat > "$APP_DIR/Info.plist" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key>
  <string>EA FC Stats</string>
  <key>CFBundleDisplayName</key>
  <string>EA FC Stats</string>
  <key>CFBundleIdentifier</key>
  <string>com.eafc26.stats.launcher</string>
  <key>CFBundleVersion</key>
  <string>1.0.0</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0.0</string>
  <key>CFBundleExecutable</key>
  <string>EA FC Stats</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>CFBundleIconFile</key>
  <string>AppIcon</string>
  <key>LSMinimumSystemVersion</key>
  <string>13.0</string>
  <key>NSHighResolutionCapable</key>
  <true/>
  <key>NSQuitAlwaysKeepsWindows</key>
  <false/>
</dict>
</plist>
EOF

# Copy compiled binary
cp "$SCRIPT_DIR/EAFCStatsLauncher" "$APP_DIR/MacOS/EA FC Stats"
chmod +x "$APP_DIR/MacOS/EA FC Stats"

# Clean build artifact
rm -f "$SCRIPT_DIR/EAFCStatsLauncher"

echo ""
echo "=== Validating ==="
plutil -lint "$APP_DIR/Info.plist"
test -x "$APP_DIR/MacOS/EA FC Stats" && echo "Executable: OK"

echo ""
echo "=== Built: $DIST_DIR/EA FC Stats.app ==="
