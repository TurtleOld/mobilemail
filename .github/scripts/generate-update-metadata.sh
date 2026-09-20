#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 <apk-path> <output-json-path>" >&2
  exit 2
}

APK_PATH="${1:-}"
OUTPUT_PATH="${2:-}"

if [ -z "$APK_PATH" ] || [ -z "$OUTPUT_PATH" ]; then
  usage
fi

if [ ! -f "$APK_PATH" ]; then
  echo "APK not found: $APK_PATH" >&2
  exit 1
fi

: "${APP_VERSION_CODE:?APP_VERSION_CODE is required}"
: "${APP_VERSION_NAME:?APP_VERSION_NAME is required}"

if ! [[ "$APP_VERSION_CODE" =~ ^[0-9]+$ ]]; then
  echo "APP_VERSION_CODE must be a number, got: $APP_VERSION_CODE" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_FILE="$REPO_ROOT/app/build.gradle.kts"

if [ ! -f "$BUILD_FILE" ]; then
  echo "Gradle build file not found: $BUILD_FILE" >&2
  exit 1
fi

APPLICATION_ID="$(sed -n 's/^[[:space:]]*applicationId = "\([^"]*\)".*/\1/p' "$BUILD_FILE" | head -n1)"
MIN_SDK="$(sed -n 's/^[[:space:]]*minSdk = \([0-9][0-9]*\).*/\1/p' "$BUILD_FILE" | head -n1)"

if [ -z "$APPLICATION_ID" ]; then
  echo "Could not read applicationId from $BUILD_FILE" >&2
  exit 1
fi

if [ -z "$MIN_SDK" ]; then
  echo "Could not read minSdk from $BUILD_FILE" >&2
  exit 1
fi

APK_SIZE_BYTES="$(wc -c < "$APK_PATH" | tr -d '[:space:]')"

if command -v sha256sum >/dev/null 2>&1; then
  APK_SHA256="$(sha256sum "$APK_PATH" | cut -d' ' -f1)"
elif command -v shasum >/dev/null 2>&1; then
  APK_SHA256="$(shasum -a 256 "$APK_PATH" | cut -d' ' -f1)"
else
  echo "No sha256 utility found (need sha256sum or shasum)" >&2
  exit 1
fi

if ! [[ "$APK_SHA256" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Unexpected sha256 for $APK_PATH: $APK_SHA256" >&2
  exit 1
fi

APK_ASSET_NAME="$(basename "$APK_PATH")"

cat > "$OUTPUT_PATH" <<JSON
{
  "schemaVersion": 1,
  "applicationId": "$APPLICATION_ID",
  "versionCode": $APP_VERSION_CODE,
  "versionName": "$APP_VERSION_NAME",
  "minSdk": $MIN_SDK,
  "apkAssetName": "$APK_ASSET_NAME",
  "apkSizeBytes": $APK_SIZE_BYTES,
  "apkSha256": "$APK_SHA256"
}
JSON

echo "Wrote $OUTPUT_PATH for $APK_ASSET_NAME ($APK_SIZE_BYTES bytes, sha256 $APK_SHA256)"
