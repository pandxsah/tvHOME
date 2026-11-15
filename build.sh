#!/usr/bin/env bash
# Builds build/TVHome.apk with plain SDK tools (no Gradle).
# Needs: JDK 17+, aapt2, dalvik-exchange (dx), zipalign, apksigner and an API 34 android.jar.
# Lambdas are rewritten into plain classes by tools/desugar/Desugar.java (needs libasm-java:
# apt install libasm-java), because the Android runtime cannot run invokedynamic lambdas.
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:?set ANDROID_JAR to an API 34 android.jar (see docs/building.md)}"
VERSION_CODE="${VERSION_CODE:-16}"
VERSION_NAME="${VERSION_NAME:-1.3.1}"
ASM=/usr/share/java/asm-9.7.jar:/usr/share/java/asm-commons-9.7.jar:/usr/share/java/asm-tree-9.7.jar:/usr/share/java/asm-analysis-9.7.jar
B=build
rm -rf "$B" && mkdir -p "$B/gen" "$B/classes" "$B/desugared" "$B/dex" "$B/tool"

aapt2 compile --dir res -o "$B/res.zip"
aapt2 link -o "$B/unsigned.apk" -I "$ANDROID_JAR" --manifest AndroidManifest.xml -A assets \
    --java "$B/gen" --min-sdk-version 26 --target-sdk-version 34 \
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" "$B/res.zip"

javac -nowarn -encoding UTF-8 --release 8 -classpath "$ANDROID_JAR" -d "$B/classes" \
    $(find src "$B/gen" -name '*.java')
javac -nowarn -cp "$ASM" -d "$B/tool" tools/desugar/Desugar.java
java -cp "$B/tool:$ASM" Desugar "$B/classes" "$B/desugared"
dalvik-exchange --dex --min-sdk-version=26 --output="$B/dex/classes.dex" "$B/desugared"
cp "$B/unsigned.apk" "$B/base.apk"
(cd "$B/dex" && zip -q ../base.apk classes.dex)
zipalign -f 4 "$B/base.apk" "$B/aligned.apk"

# Sign with YOUR key. Every update must use the same key, or Android will not install it over the old one.
#   keytool -genkeypair -keystore tvhome.keystore -alias tvhome -keyalg RSA -keysize 2048 -validity 36500
#   KEYSTORE_PASS=yourpassword ./build.sh
: "${KEYSTORE_PASS:?set KEYSTORE_PASS to your keystore password (see docs/building.md)}"
apksigner sign --ks "${KEYSTORE:-tvhome.keystore}" --ks-pass env:KEYSTORE_PASS --key-pass env:KEYSTORE_PASS \
    --ks-key-alias "${KEY_ALIAS:-tvhome}" --out "$B/TVHome.apk" "$B/aligned.apk"
rm -f "$B"/*.idsig
echo "Built $B/TVHome.apk"
