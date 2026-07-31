#!/usr/bin/env bash
# scripts/package-macos.sh
# Backward-compatible wrapper for the Gradle-native macOS packaging.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

exec "$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" rebuildMacApp "$@"
