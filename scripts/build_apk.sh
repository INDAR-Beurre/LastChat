#!/usr/bin/env bash
set -euo pipefail

# -----------------------------------------------------------------------------
# LastLab Mobile — APK Builder
# Designed for low-spec PCs: supports both:
# 1. Zero-daemon lightweight APK builder (2.0 MB, <150MB RAM, ~0.7s)
# 2. Authentic full Gradle CLI builder (--full / -f)
# -----------------------------------------------------------------------------

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN_DIR="$PROJECT_DIR/toolchain"
MOBILE_DIR="$PROJECT_DIR/mobile"
BUILD_DIR="$MOBILE_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"

MODE="lightweight"
for arg in "$@"; do
    if [ "$arg" = "--full" ] || [ "$arg" = "-f" ]; then
        MODE="full"
    fi
done

echo "=== LastLab Mobile APK Build [Mode: $MODE] ==="
echo "Project Root: $PROJECT_DIR"

# 1. Locate JDK (Java 17+). Honor an explicit JAVA_HOME first, then the
# per-user toolchain, then whatever javac is on PATH.
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    :
elif [ -d "$HOME/.local/jdk-17" ]; then
    JAVA_HOME="$HOME/.local/jdk-17"
elif [ -d "/tmp/android-toolchain/jdk" ]; then
    JAVA_HOME="/tmp/android-toolchain/jdk"
elif command -v javac >/dev/null 2>&1; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which javac)")")")"
else
    echo "ERROR: Java 17+ not found. Set JAVA_HOME or install a JDK 17." >&2
    exit 1
fi

if [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "ERROR: JAVA_HOME=$JAVA_HOME has no bin/java." >&2
    exit 1
fi

JAVA="$JAVA_HOME/bin/java"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

echo "Using Java: $("$JAVA" -version 2>&1 | head -n 1)"

mkdir -p "$DIST_DIR"

if [ "$MODE" = "full" ]; then
    # Full Gradle CLI build
    if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
        :
    elif [ -d "$HOME/android-sdk" ]; then
        export ANDROID_HOME="$HOME/android-sdk"
    elif [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    fi
    echo "[1/2] Building authentic full-engine LastLab APK with Gradle..."
    cd "$PROJECT_DIR"
    ./gradlew :app:assembleStableRelease \
        -Plastchat.release.minify=false \
        --max-workers=2 \
        --no-daemon
    
    echo "[2/2] Generating checksums for generated APKs..."
    (cd "$PROJECT_DIR/app/build/outputs/apk/stable/release" \
        && sha256sum ./*.apk > "$DIST_DIR/full-release-apks.sha256")
    echo "Full release APKs built in app/build/outputs/apk/stable/release/:"
    ls -lh "$PROJECT_DIR/app/build/outputs/apk/stable/release/"*.apk
    exit 0
fi

# Default lightweight APK build (<150MB RAM, ~0.7s)
AAPT2="$TOOLCHAIN_DIR/aapt2"
R8_JAR="$TOOLCHAIN_DIR/r8.jar"
ANDROID_JAR="$TOOLCHAIN_DIR/android.jar"
SIGNER_JAR="$TOOLCHAIN_DIR/uber-apk-signer.jar"

for tool in "$AAPT2" "$R8_JAR" "$ANDROID_JAR" "$SIGNER_JAR"; do
    if [ ! -f "$tool" ]; then
        echo "ERROR: Missing required toolchain binary: $tool"
        exit 1
    fi
done

chmod +x "$AAPT2"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/obj" "$BUILD_DIR/dex" "$DIST_DIR"

# Ensure mobile assets are synced with web-ui
if [ -d "$PROJECT_DIR/web-ui/build/client" ]; then
    rsync -a --delete "$PROJECT_DIR/web-ui/build/client/" "$MOBILE_DIR/assets/www/" 2>/dev/null || cp -rf "$PROJECT_DIR/web-ui/build/client/"* "$MOBILE_DIR/assets/www/"
fi

echo "[1/6] Compiling Android resources..."
"$AAPT2" compile --dir "$MOBILE_DIR/res" -o "$BUILD_DIR/compiled_res.zip"

echo "[2/6] Linking APK with AAPT2 & bundling assets..."
"$AAPT2" link \
    -I "$ANDROID_JAR" \
    --manifest "$MOBILE_DIR/AndroidManifest.xml" \
    -o "$BUILD_DIR/base.apk" \
    -A "$MOBILE_DIR/assets" \
    --java "$BUILD_DIR/gen" \
    "$BUILD_DIR/compiled_res.zip" \
    --auto-add-overlay

echo "[3/6] Compiling Java classes with javac..."
"$JAVAC" -cp "$ANDROID_JAR" \
    -d "$BUILD_DIR/obj" \
    $(find "$MOBILE_DIR/src" -name "*.java") \
    $(find "$BUILD_DIR/gen" -name "*.java")

echo "[4/6] Dexing bytecode with D8..."
CLASS_FILES=$(find "$BUILD_DIR/obj" -name "*.class")
"$JAVA" -cp "$R8_JAR" com.android.tools.r8.D8 \
    $CLASS_FILES \
    --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR/dex"

echo "[5/6] Packaging DEX into APK..."
"$JAR" -uf "$BUILD_DIR/base.apk" -C "$BUILD_DIR/dex" classes.dex

echo "[6/6] Aligning & signing APK..."
"$JAVA" -jar "$SIGNER_JAR" \
    --apks "$BUILD_DIR/base.apk" \
    -o "$DIST_DIR"

SIGNED_APK=$(find "$DIST_DIR" -name "base-aligned-*.apk" | head -n 1)
FINAL_APK="$DIST_DIR/lastlab.apk"
mv -f "$SIGNED_APK" "$FINAL_APK"
(cd "$DIST_DIR" && sha256sum "$(basename "$FINAL_APK")" > "lastlab.apk.sha256")

echo "================================================="
echo "✓ BUILD SUCCESSFUL!"
echo "Output APK: $FINAL_APK"
echo "Size: $(du -h "$FINAL_APK" | cut -f1)"
echo "================================================="

"$AAPT2" dump badging "$FINAL_APK" | head -n 15 || true
