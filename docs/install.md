# Install guide

This installs TV Home on an Android TV or Google TV and makes it the home screen. You only need a PC for the first install. After that, updates come from the app itself.

**You will need**

- An Android TV or Google TV, and a PC on the same Wi-Fi or Ethernet network.
- [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (contains `adb`). Unzip it anywhere, for example `C:\platform-tools`.
- `TVHome.apk` from the [latest release](../../../releases/latest).

## 1. Enable debugging on the TV

1. Open **Settings → System → About** and click **Build** (or **Android TV OS build**) seven times until it says you are a developer.
2. Go back to **Settings → System → Developer options**.
3. Turn on **USB debugging**, and **Network debugging** (called **Wireless debugging** on some TVs).
4. Find the TV's IP address under **Settings → Network & Internet** (for example `192.168.1.12`).

Menu names differ between brands, so look for Developer options in the settings search if you cannot find it.

## 2. Connect from the PC

Open a terminal (Command Prompt on Windows) in the platform-tools folder.

```
adb connect 192.168.1.12:5555
adb devices
```

Look at the TV. An **Allow USB debugging?** dialog appears. Tick **Always allow** and select **Allow**. Then `adb devices` should list your TV as `device`. If it says `unauthorized`, see [Troubleshooting](troubleshooting.md#adb-says-unauthorized).

## 3. Install the APK

```
adb install -r path\to\TVHome.apk
```

`-r` keeps your settings if TV Home is already installed. You should see `Success`.

## 4. Make TV Home the home screen

Android TV does not always show a "choose default launcher" prompt. This command sets it directly:

```
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.pandxsah.tvhome
```

Check it worked:

```
adb shell dumpsys role | findstr "holders"
```

(On Mac/Linux use `grep holders` instead of `findstr`.) Your package should appear as a holder of the HOME role. Press the Home button on the remote.

This does not disable or uninstall your old launcher, so you can always switch back.

## 5. First-run setup

On first launch TV Home asks you to pick a permissions mode:

| Mode | What it does | Pick it if |
|---|---|---|
| **Secure Auto Setup & Close ADB** | Grants the needed permissions once, then closes network ADB again. | You want the safest option and do not need the debloater often. |
| **Persistent ADB Setup (Keep ADB Open)** | Keeps network ADB enabled so the built-in debloater can work later. | You plan to remove preinstalled apps from inside TV Home. |
| **Manual Setup (Without ADB)** | You grant each permission by hand in Android TV settings. | You do not want to use ADB at all. |

Network ADB stays reachable on your local network but only to devices you have authorized.

For the **media player widget** to see what is playing, TV Home needs notification access. The two ADB modes enable it for you. In manual mode, allow it under Android TV settings → Apps → Special access → Notification access.

## Restore the original launcher

Set your old launcher as the home app again. For the stock Google TV launcher:

```
adb shell cmd role add-role-holder --user 0 android.app.role.HOME com.google.android.apps.tv.launcherx
```

For other TVs, find the package of your old launcher with `adb shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME` and use its package name instead.

To uninstall TV Home completely: `adb uninstall com.pandxsah.tvhome`.

## Next

- [Updating TV Home](updating.md)
- [Troubleshooting](troubleshooting.md)
