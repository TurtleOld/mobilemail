#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/sha256.sh"

usage() {
  echo "Usage: $0 <release-tag> <asset-name> <local-file>" >&2
  exit 2
}

RELEASE_TAG="${1:-}"
ASSET_NAME="${2:-}"
LOCAL_FILE="${3:-}"

if [ -z "$RELEASE_TAG" ] || [ -z "$ASSET_NAME" ] || [ -z "$LOCAL_FILE" ]; then
  usage
fi

if [ ! -f "$LOCAL_FILE" ]; then
  echo "Local file not found: $LOCAL_FILE" >&2
  exit 1
fi

REPOSITORY="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY environment variable is required}"

fail_immutable() {
  echo "Release '$RELEASE_TAG' already contains '$ASSET_NAME' with different bytes." >&2
  echo "Release tags are immutable; cut a new tag instead of overwriting published assets." >&2
  exit 1
}

LOCAL_SHA256="$(sha256_of "$LOCAL_FILE")"

EXISTING_DIGEST="$(gh api "repos/$REPOSITORY/releases/tags/$RELEASE_TAG" \
  --jq ".assets[] | select(.name == \"$ASSET_NAME\") | .digest" | head -n1)"

if [ -z "$EXISTING_DIGEST" ]; then
  echo "No existing asset '$ASSET_NAME' in release '$RELEASE_TAG'; publishing is safe."
  exit 0
fi

if [ "$EXISTING_DIGEST" != "null" ]; then
  if [ "$EXISTING_DIGEST" = "sha256:$LOCAL_SHA256" ]; then
    echo "Asset '$ASSET_NAME' matches the local bytes; idempotent republish is safe."
    exit 0
  fi
  fail_immutable
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

gh release download "$RELEASE_TAG" \
  --repo "$REPOSITORY" \
  --pattern "$ASSET_NAME" \
  --dir "$TMP_DIR" \
  --clobber

DOWNLOADED_FILE="$TMP_DIR/$ASSET_NAME"

if [ ! -f "$DOWNLOADED_FILE" ]; then
  echo "Could not download existing asset '$ASSET_NAME' from release '$RELEASE_TAG'." >&2
  exit 1
fi

DOWNLOADED_SHA256="$(sha256_of "$DOWNLOADED_FILE")"

if [ "$DOWNLOADED_SHA256" = "$LOCAL_SHA256" ]; then
  echo "Asset '$ASSET_NAME' matches the local bytes; idempotent republish is safe."
  exit 0
fi

fail_immutable
