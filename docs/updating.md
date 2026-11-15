# Updating

## As a user

1. Open **Settings → Updates → Check for updates** in TV Home.
2. If a newer release exists, choose **Install**. TV Home downloads the APK and installs it.
   - With **Persistent ADB** mode, the install runs silently.
   - Otherwise Android shows its normal install prompt. Confirm it.
3. Your settings are kept.

Automatic update checks are **off by default**. You can enable them in the same screen.

TV Home compares the installed version name with the latest release tag on this repository, so tags must look like `v1.4`.

## As the maintainer: publishing a release

1. Bump the version in `build.sh` (`VERSION_CODE` must go up every time, `VERSION_NAME` is shown to users).
2. Build and sign the APK (see [building.md](building.md)).
3. On GitHub: **Releases → Draft a new release**.
4. Tag: `v` + the version name, for example `v1.4`. Add short release notes. They are shown as the changelog in the app.
5. Attach the signed APK, named `TVHome.apk`.
6. Publish.

**Always sign with the same key.** Android refuses to install an update that was signed with a different key than the installed version. If you lose the key, existing installs must be uninstalled before a new build can be installed. Never commit your keystore. `.gitignore` already excludes `*.keystore`.

The repository must be public, because the TV downloads the APK without logging in.
