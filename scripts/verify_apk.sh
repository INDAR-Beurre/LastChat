#!/usr/bin/env bash
# Confirms the built APK actually contains the pieces it claims to.
# Build output is easy to fake; this reads the shipped artifact.
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "$(readlink -f "${BASH_SOURCE[0]}")")/.." && pwd)"
cd "$ROOT"

APK="${1:-dist/lastlab.apk}"
AAPT2="toolchain/aapt2"

if [ ! -f "$APK" ]; then
  echo "missing APK: $APK" >&2
  exit 1
fi

echo "== badging =="
badging="$("$AAPT2" dump badging "$APK")"
printf '%s\n' "$badging" | sed -n '1,3p'

if ! printf '%s\n' "$badging" | grep -q "versionCode='1' versionName='1.0.0'"; then
  echo "FAIL  unexpected versionCode/versionName" >&2
  exit 1
fi
echo "OK  versionCode 1 / versionName 1.0.0"

echo
echo "== server classes in dex =="
# Unzip classes.dex and list the app's own classes: the backend is the whole point of the fork.
_tmp="$(mktemp -d)"
trap 'rm -rf "$_tmp"' EXIT
unzip -o -q "$APK" 'classes*.dex' -d "$_tmp"

dex_symbols="$(strings -a "$_tmp"/classes*.dex)"

if printf '%s\n' "$dex_symbols" | grep -q 'com/relay/lastlab/server/ApiServer'; then
  echo "OK  com/relay/lastlab/server/* present"
  printf '%s\n' "$dex_symbols" \
    | grep -oE 'com/relay/lastlab/server/[A-Za-z]+' \
    | sort -u | sed 's/^/    /'
else
  echo "FAIL  com/relay/lastlab/server/ApiServer not found in dex" >&2
  exit 1
fi

echo
echo "== SPA assets =="
apk_listing="$(unzip -l "$APK")"
if printf '%s\n' "$apk_listing" | grep -q 'assets/www/index.html'; then
  echo "OK  assets/www/index.html present"
  echo "    index.html entry: $(unzip -p "$APK" assets/www/index.html | grep -c 'assets/root-')"
else
  echo "FAIL  assets/www/index.html missing" >&2
  exit 1
fi

# A root-absolute local URL would 404 against the loopback origin.
if unzip -p "$APK" assets/www/index.html | grep -qE '(src|href)="/'; then
  echo "FAIL  built index.html still has a root-absolute local URL" >&2
  unzip -p "$APK" assets/www/index.html | grep -oE '(src|href)="/[^"]*"' >&2
  exit 1
fi
echo "OK  no root-absolute local URLs"

echo
echo "APK VERIFY PASS"