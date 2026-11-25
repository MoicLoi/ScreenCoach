#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app/src/main"
BUILD="$ROOT/build"

if [[ -n "${ANDROID_HOME:-}" ]]; then
  SDK="$ANDROID_HOME"
elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
  SDK="$ANDROID_SDK_ROOT"
elif [[ -d "$HOME/tools/android-sdk" ]]; then
  SDK="$HOME/tools/android-sdk"
elif [[ -d "$HOME/Android/Sdk" ]]; then
  SDK="$HOME/Android/Sdk"
else
  echo "Android SDK not found. Set ANDROID_HOME." >&2
  exit 1
fi

ANDROID_JAR="$SDK/platforms/android-33/android.jar"
BUILD_TOOLS="$SDK/build-tools/34.0.0"
if [[ ! -f "$ANDROID_JAR" || ! -x "$BUILD_TOOLS/aapt2" ]]; then
  echo "Need platforms/android-33 and build-tools/34.0.0 under $SDK" >&2
  exit 1
fi

PACKAGE="com.moiclathy.screencoach"
VERSION_NAME="1.0.0"
VERSION_CODE="1"

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$ROOT/dist"

# AGP 8 expects the namespace in Gradle rather than AndroidManifest.xml. This
# manual build injects the package attribute only into a temporary manifest.
python3 - "$APP/AndroidManifest.xml" "$BUILD/AndroidManifest.xml" "$PACKAGE" <<'PY'
import sys
src, dst, package = sys.argv[1:4]
text = open(src, encoding="utf-8").read()
needle = '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
if needle not in text:
    raise SystemExit("unexpected manifest root")
text = text.replace(needle, needle[:-1] + f' package="{package}">', 1)
open(dst, "w", encoding="utf-8").write(text)
PY

"$BUILD_TOOLS/aapt2" compile --dir "$APP/res" -o "$BUILD/res.zip"
"$BUILD_TOOLS/aapt2" link \
  -o "$BUILD/app-unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$BUILD/AndroidManifest.xml" \
  --java "$BUILD/gen" \
  -R "$BUILD/res.zip" \
  --auto-add-overlay \
  --min-sdk-version 30 \
  --target-sdk-version 33 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME"

find "$APP/java" "$BUILD/gen" -name '*.java' -print > "$BUILD/sources.txt"
javac -source 8 -target 8 -Xlint:-options -parameters -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR:$BUILD_TOOLS/core-lambda-stubs.jar" \
  -d "$BUILD/classes" \
  @"$BUILD/sources.txt"

find "$BUILD/classes" -name '*.class' -print > "$BUILD/classes.txt"
"$BUILD_TOOLS/d8" --lib "$ANDROID_JAR" --min-api 30 \
  --output "$BUILD/dex" @"$BUILD/classes.txt"

(cd "$BUILD/dex" && zip -q -j "$BUILD/app-unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD/app-unsigned.apk" "$BUILD/app-aligned.apk"

KEYSTORE="$ROOT/dist/screencoach-debug.keystore"
if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass android -keypass android \
    -alias androiddebugkey \
    -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi

OUT="$ROOT/dist/ScreenCoach-${VERSION_NAME}-debug.apk"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$OUT" \
  "$BUILD/app-aligned.apk"
"$BUILD_TOOLS/apksigner" verify --print-certs "$OUT" >/dev/null

echo "$OUT"
