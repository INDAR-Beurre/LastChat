#!/usr/bin/env bash
# Runs the backend against a scripted relay and leaves it listening, so a browser can drive the
# same server the assertions cover. Prints the base URL once it is accepting connections.
#
# Usage: bash scripts/smoke/serve.sh [seconds]   (default 900)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

JDK="${JAVA_HOME:-$HOME/.local/jdk-17}"

if [ ! -f mobile/assets/www/index.html ]; then
  echo "missing mobile/assets/www/index.html; run scripts/build_apk.sh first" >&2
  exit 1
fi

echo "== compiling =="
OUT="build/serve-classes"
rm -rf "$OUT"
mkdir -p "$OUT"
"$JDK/bin/javac" -nowarn -cp "toolchain/android.jar" -d "$OUT" \
  $(find mobile/src/com/relay/lastlab/server -name '*.java' ! -name 'AndroidPlatform.java') \
  scripts/smoke/FakeRelay.java \
  scripts/smoke/SmokeMain.java \
  scripts/smoke/RestartMain.java

rm -rf build/serve-store
mkdir -p build/serve-store

# Classes live under build/ rather than a temporary directory so the server keeps running when
# this script exits, and so a transient service unit does not take the JVM down with it.
echo "== serving =="
exec "$JDK/bin/java" -cp "$OUT:toolchain/json.jar" 'smoke.SmokeMain$Serve' "${1:-900}"