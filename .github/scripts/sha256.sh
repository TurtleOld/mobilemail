#!/usr/bin/env bash
set -euo pipefail

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | cut -d' ' -f1
  else
    echo "No sha256 utility found (need sha256sum or shasum)" >&2
    return 1
  fi
}
