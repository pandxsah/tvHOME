# Troubleshooting

## adb says `unauthorized`

The TV does not trust your PC yet or has forgotten it.

1. On the TV: **Developer options → Revoke USB debugging authorizations**, then turn USB debugging off and on.
2. On the PC:
   ```
   adb disconnect
   adb kill-server
   adb start-server
   adb connect <TV-IP>:5555
   ```
3. Watch the TV. The **Allow USB debugging?** dialog appears for a few seconds. Tick **Always allow** and select **Allow**.
4. Check with `adb devices`. It should say `device`.

If no dialog ever appears, delete the PC's key so a new one is created, then reconnect:

- Windows: delete `%USERPROFILE%\.android\adbkey` and `adbkey.pub`
- Mac/Linux: delete `~/.android/adbkey` and `adbkey.pub`

If you have several copies of `adb` on the PC, use only one. Mixed versions can cause this.

## TV Home opens for a second and the old launcher comes back

That means TV Home crashed on start and Android handed the Home button to the next launcher.

1. Open TV Home again. If it crashed last time, a **"TV Home crashed last time"** box shows the error. Open an [issue](../../../issues) with a photo or the text.
2. Without the box, or if it keeps crashing, collect the log: `adb logcat -b crash -d`.
3. Restore your old launcher in the meantime: [Restore the original launcher](install.md#restore-the-original-launcher).

## The media widget says "Nothing playing"

It needs **notification access** to see media sessions.

- The Persistent and Secure ADB setup modes enable it.
- Manual: Android TV settings → Apps → Special access → Notification access → turn on TV Home.
- Start playback in a media app, then return Home.

Some apps do not publish a media session while they are idle.

## The Home button does not open TV Home

Run the role command from the [install guide](install.md#4-make-tv-home-the-home-screen) again and confirm with `adb shell dumpsys role`. Some TVs reset it after a system update.

## "Check for updates" fails

- The TV needs internet access.
- The repository must have at least one published release whose tag looks like `v1.4`, with an `.apk` attached.
- The release must be newer than the installed version.

## Settings or a screen will not open

Open TV Home again and look for the crash box, and report the text with your TV model and Android version.
