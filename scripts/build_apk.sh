#!/usr/bin/env bash
set -euo pipefail

# -----------------------------------------------------------------------------
# LastChat Playground Mobile — Lightweight APK Builder
# Designed for low-spec PCs: 0 heavy daemons, ~150MB RAM max, builds in 2s.
# -----------------------------------------------------------------------------

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN_DIR="$PROJECT_DIR/toolchain"
MOBILE_DIR="$PROJECT_DIR/mobile"
BUILD_DIR="$MOBILE_DIR/build"
DIST_DIR="$PROJECT_DIR/dist"

echo "=== LastChat Mobile APK Build ==="
echo "Project Root: $PROJECT_DIR"

# 1. Locate JDK (Java 17+)
if [ -d "/home/alex/.local/jdk-17" ]; then
    JAVA_HOME="/home/alex/.local/jdk-17"
elif [ -d "/tmp/android-toolchain/jdk" ]; then
    JAVA_HOME="/tmp/android-toolchain/jdk"
elif command -v javac >/dev/null 2>&1; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which javac)")")")"
else
    echo "ERROR: Java 17+ not found. Please ensure JDK 17 is installed."
    exit 1
fi

JAVA="$JAVA_HOME/bin/java"
JAVAC="$JAVA_HOME/bin/javac"
JAR="$JAVA_HOME/bin/jar"

echo "Using Java: $("$JAVA" -version 2>&1 | head -n 1)"

# 2. Verify Toolchain
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

# 3. Clean & Prepare Build Directories
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/obj" "$BUILD_DIR/dex" "$DIST_DIR"

# 4. Compile Android Resources with AAPT2
echo "[1/6] Compiling Android resources..."
"$AAPT2" compile --dir "$MOBILE_DIR/res" -o "$BUILD_DIR/compiled_res.zip"

# 5. Link Resources, Assets & Manifest into Base APK
echo "[2/6] Linking APK with AAPT2 & bundling assets..."
"$AAPT2" link \
    -I "$ANDROID_JAR" \
    --manifest "$MOBILE_DIR/AndroidManifest.xml" \
    -o "$BUILD_DIR/base.apk" \
    -A "$MOBILE_DIR/assets" \
    --java "$BUILD_DIR/gen" \
    "$BUILD_DIR/compiled_res.zip" \
    --auto-add-overlay

# 6. Compile Java Source Code
echo "[3/6] Compiling Java classes with javac..."
"$JAVAC" -cp "$ANDROID_JAR" \
    -d "$BUILD_DIR/obj" \
    $(find "$MOBILE_DIR/src" -name "*.java") \
    $(find "$BUILD_DIR/gen" -name "*.java")

# 7. Convert Java Bytecode to Dalvik DEX via D8
echo "[4/6] Dexing bytecode with D8..."
CLASS_FILES=$(find "$BUILD_DIR/obj" -name "*.class")
"$JAVA" -cp "$R8_JAR" com.android.tools.r8.D8 \
    $CLASS_FILES \
    --lib "$ANDROID_JAR" \
    --output "$BUILD_DIR/dex"

# 8. Package classes.dex into APK
echo "[5/6] Packaging DEX into APK..."
"$JAR" -uf "$BUILD_DIR/base.apk" -C "$BUILD_DIR/dex" classes.dex

# 9. Zipalign and Cryptographically Sign APK (v1, v2, v3)
echo "[6/6] Aligning & signing APK..."
"$JAVA" -jar "$SIGNER_JAR" \
    --apks "$BUILD_DIR/base.apk" \
    -o "$DIST_DIR"

# Standardize output name
SIGNED_APK=$(find "$DIST_DIR" -name "base-aligned-*.apk" | head -n 1)
FINAL_APK="$DIST_DIR/lastchat-playground.apk"
mv -f "$SIGNED_APK" "$FINAL_APK"

echo "================================================="
echo "✓ BUILD SUCCESSFUL!"
echo "Output APK: $FINAL_APK"
echo "Size: $(du -h "$FINAL_APK" | cut -f1)"
echo "================================================="

# Dump badging info
"$AAPT2" dump badging "$FINAL_APK" | head -n 15 || true
