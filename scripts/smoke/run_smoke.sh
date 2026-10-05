#!/usr/bin/env bash
# End-to-end smoke test for the in-app backend.
#
# Compiles the server package together with a fake relay and runs real assertions against
# real sockets. Exits non-zero when any assertion fails.
#
# Note on org.json: the app runs on Android, which ships a real org.json, so the APK is
# compiled against toolchain/android.jar. That jar's org.json is a compile-time stub whose
# methods throw "Stub!" at runtime, so the smoke run is linked against toolchain/json.jar —
# the reference implementation of the same API — instead.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

JDK="${JAVA_HOME:-$HOME/.local/jdk-17}"
JAVAC="$JDK/bin/javac"
JAVA="$JDK/bin/java"

OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

echo "== compiling =="
"$JAVAC" -nowarn -cp "toolchain/android.jar" -d "$OUT" \
  $(find mobile/src/com/relay/lastlab/server -name '*.java' ! -name 'AndroidPlatform.java') \
  scripts/smoke/FakeRelay.java \
  scripts/smoke/SmokeMain.java \
  scripts/smoke/RestartMain.java

# Each run gets its own store directory so a run never inherits another's state.
TMPDIRS=""

new_store() {
  local d
  d="$(mktemp -d)"
  TMPDIRS="$TMPDIRS $d"
  export SMOKE_TMPDIR="$d"
}

cleanup() {
  rm -rf "$OUT"
  for d in $TMPDIRS; do rm -rf "$d"; done
}
trap cleanup EXIT

echo "== running =="
new_store
"$JAVA" -cp "$OUT:toolchain/json.jar" smoke.SmokeMain

echo
echo "== restart persistence =="
new_store
"$JAVA" -cp "$OUT:toolchain/json.jar" smoke.RestartMain