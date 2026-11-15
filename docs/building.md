# Building from source

TV Home builds with a single shell script and plain Android SDK command-line tools. There is no Gradle.

## Requirements

Linux or macOS with:

- JDK 17 or newer
- `aapt2`, `zipalign`, `apksigner` and `dalvik-exchange` (`dx`) from the Android build tools. On Debian/Ubuntu: `sudo apt install aapt apksigner zipalign dalvik-exchange libasm-java`
- An API 34 `android.jar` (from the Android SDK `platforms/android-34` folder)

## Why a custom desugarer?

Java lambdas compile to `invokedynamic`. `dx` at this API level turns those into instructions that Android's runtime cannot execute, which makes the app crash on launch. `tools/desugar/Desugar.java` rewrites every lambda into an ordinary generated class before dexing. It needs the ASM library (`libasm-java`). If you build with Android Gradle Plugin / D8 instead, you don't need it.

## Create a signing key (once)

```
keytool -genkeypair -keystore tvhome.keystore -alias tvhome -keyalg RSA -keysize 2048 -validity 36500
```

Keep this file and its password safe and private. All future updates must use the same key.

## Build

```
export ANDROID_JAR=/path/to/android-34.jar
export KEYSTORE_PASS=your-keystore-password
./build.sh
```

Output: `build/TVHome.apk`. Override `VERSION_CODE` and `VERSION_NAME` on the command line to change the version.

Install it with `adb install -r build/TVHome.apk`.

## Project layout

```
AndroidManifest.xml        package com.pandxsah.tvhome
build.sh                   the whole build
src/com/pandxsah/tvhome/   the launcher (Java)
src/io/nayuki/qrcodegen/   vendored QR encoder (MIT)
res/, assets/              icons, XML resources, fonts, bundled assets
tools/desugar/             lambda desugarer used by build.sh
docs/                      documentation (docs/upstream = original project's docs)
```

For how the launcher is structured internally, see [docs/upstream/architecture.md](upstream/architecture.md). Package paths in that file refer to the original `com.techgenius.tvoslauncher` layout.
