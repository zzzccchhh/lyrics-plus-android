#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

if [[ -z "${LYRICS_PLUS_STATS_ENDPOINT:-}" && -f "${SCRIPT_DIR}/official-build.env" ]]; then
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/official-build.env"
fi

if [[ -z "${LYRICS_PLUS_STATS_ENDPOINT:-}" ]]; then
  echo "Missing LYRICS_PLUS_STATS_ENDPOINT for official release build." >&2
  exit 1
fi

export ORG_GRADLE_PROJECT_lyricsPlusStatsEndpoint="${LYRICS_PLUS_STATS_ENDPOINT}"

cd "${REPO_ROOT}"
./gradlew assembleRelease "$@"

