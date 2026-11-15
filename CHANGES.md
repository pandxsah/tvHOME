# Changes from tvOS Launcher 2.0.6

- Renamed to TV Home; package `com.pandxsah.tvhome` (source moved to `src/com/pandxsah/tvhome`).
- Automatic and manual update checks disabled (they pointed at the original project's releases).
- Build script replaced: `build.sh` uses aapt2 + javac (Java 8) + a lambda desugarer (tools/desugar) + dx at minSdk 26, signed with `tvhome.keystore`.
- Setup/About credit line now reads "Based on tvOS Launcher by techgenius (Apache 2.0)".

## Added in TV Home

- Media player widget (any media app) with play/pause, previous/next and last-played fallback.
- Custom app widgets; hold OK on a widget to remove it; Prayer/Football/Most Watched off by default.
- Customize home screen hub; Down on the dock opens all apps; extra home-screen focus stop between widgets and the top-right buttons.
- Update check and install re-enabled, pointed at https://github.com/pandxsah/tvHOME/releases (automatic checks off by default).
- On-screen crash report shown at the next launch after a crash.
- Lambda desugarer so the app runs on Android 11 without Gradle/D8; signing key password read from the environment.

Planned: edit mode with drag and drop, more widget types, phone remote.
