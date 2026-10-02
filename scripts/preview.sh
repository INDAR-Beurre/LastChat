#!/usr/bin/env bash
set -euo pipefail

# -----------------------------------------------------------------------------
# LastLab — Launch Preview Simulator
# Designed for low-spec PCs: 0 heavy tools, ~22MB RAM, instant launch.
# -----------------------------------------------------------------------------

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v node >/dev/null 2>&1; then
    echo "ERROR: Node.js (v18+) is required to run the lightweight preview server."
    exit 1
fi

exec node "$PROJECT_DIR/scripts/preview_server.mjs" "$@"
