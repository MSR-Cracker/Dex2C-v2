#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
BUILD="$SCRIPT_DIR/build"
LIB="$SCRIPT_DIR/lib"
CLASSES="$BUILD/classes"
FAT="$BUILD/fat"

mkdir -p "$LIB" "$CLASSES" "$FAT"

download() {
    url="$1"
    out="$2"
    if [ -f "$out" ]; then
        return
    fi
    if command -v curl >/dev/null 2>&1; then
        curl -L# "$url" -o "$out"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$out" "$url"
    else
        echo "curl or wget is required" >&2
        exit 1
    fi
}

download "https://repo1.maven.org/maven2/org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar" "$LIB/dexlib2-2.5.2.jar"
download "https://repo1.maven.org/maven2/org/smali/baksmali/2.5.2/baksmali-2.5.2.jar" "$LIB/baksmali-2.5.2.jar"
download "https://repo1.maven.org/maven2/org/smali/smali/2.5.2/smali-2.5.2.jar" "$LIB/smali-2.5.2.jar"
download "https://repo1.maven.org/maven2/org/smali/util/2.5.2/util-2.5.2.jar" "$LIB/util-2.5.2.jar"
download "https://repo1.maven.org/maven2/com/google/guava/guava/27.1-android/guava-27.1-android.jar" "$LIB/guava-27.1-android.jar"
download "https://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar" "$LIB/jsr305-3.0.2.jar"
download "https://repo1.maven.org/maven2/com/beust/jcommander/1.64/jcommander-1.64.jar" "$LIB/jcommander-1.64.jar"
download "https://repo1.maven.org/maven2/org/antlr/antlr/3.5.2/antlr-3.5.2.jar" "$LIB/antlr-3.5.2.jar"
download "https://repo1.maven.org/maven2/org/antlr/antlr-runtime/3.5.2/antlr-runtime-3.5.2.jar" "$LIB/antlr-runtime-3.5.2.jar"
download "https://repo1.maven.org/maven2/org/antlr/stringtemplate/3.2.1/stringtemplate-3.2.1.jar" "$LIB/stringtemplate-3.2.1.jar"
download "https://repo1.maven.org/maven2/io/github/reandroid/ARSCLib/1.3.8/ARSCLib-1.3.8.jar" "$LIB/ARSCLib-1.3.8.jar"

rm -rf "$CLASSES" "$FAT"
mkdir -p "$CLASSES" "$FAT"

CP=$(find "$LIB" -name '*.jar' | tr '\n' ':')
find "$SCRIPT_DIR/src" -name '*.java' > "$BUILD/sources.txt"
javac --release 17 -encoding UTF-8 -cp "$CP" -d "$CLASSES" @"$BUILD/sources.txt"

for dep in "$LIB"/*.jar; do
    (cd "$FAT" && jar xf "$dep")
done
rm -rf "$FAT/META-INF"
cp -R "$CLASSES"/. "$FAT"/
mkdir -p "$FAT/dex2c-tools"

APKSIGNER="$ROOT/tools/apksigner.jar"
if [ ! -f "$APKSIGNER" ]; then
    SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    if [ -n "$SDK_ROOT" ]; then
        APKSIGNER=$(find "$SDK_ROOT/build-tools" -path '*/lib/apksigner.jar' 2>/dev/null | sort -r | head -n 1)
    fi
fi
if [ ! -f "$APKSIGNER" ]; then
    echo "Missing apksigner.jar. Install Android SDK build-tools or place tools/apksigner.jar." >&2
    exit 1
fi
cp "$APKSIGNER" "$FAT/dex2c-tools/apksigner.jar"

cat > "$BUILD/MANIFEST.MF" <<'EOF'
Manifest-Version: 1.0
Main-Class: com.dex2c.cli.Main

EOF

jar cfm "$ROOT/dex2cxx.jar" "$BUILD/MANIFEST.MF" -C "$FAT" .
rm -rf "$BUILD" "$LIB"

echo "Built $ROOT/dex2cxx.jar"
