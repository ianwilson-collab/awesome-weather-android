#!/usr/bin/env bash
# Runs inside the emulator job: on-device tests, then real screenshots of the signed APK.
set -euo pipefail

PKG=com.prostellis.awe
mkdir -p screens/widgets
exec > >(tee -a screens/device-check.log) 2>&1
trap 'echo "::error title=Device check::Stopped at line $LINENO: $BASH_COMMAND"' ERR

# Widget drawings and the home-screen view check (debug build; left installed so the pictures can be pulled)
bash .github/scripts/gradle.sh connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
adb pull "/sdcard/Android/data/$PKG/files/renders/." screens/widgets/
cp -r app/build/outputs/androidTest-results screens/test-results 2>/dev/null || true

# Swap in the signed release APK (different signature, so the debug build comes off first)
adb uninstall "$PKG.test" || true
adb uninstall "$PKG" || true
adb install AWE-v*.apk
adb shell setprop persist.sys.timezone America/Los_Angeles

launch() {
  adb shell am force-stop "$PKG"
  adb shell am start -n "$PKG/.MainActivity" > /dev/null
}

# Loading screen (captured right after launch), then the app once the forecast has loaded
adb shell cmd uimode night no
launch
sleep 0.4
adb exec-out screencap -p > screens/loading-screen.png
sleep 14
adb exec-out screencap -p > screens/app-light.png

# Scroll to the bottom to show the footer line
adb shell input swipe 540 1800 540 300 300
adb shell input swipe 540 1800 540 300 300
adb shell input swipe 540 1800 540 300 300
sleep 1
adb exec-out screencap -p > screens/app-light-footer.png

adb shell cmd uimode night yes
launch
sleep 14
adb exec-out screencap -p > screens/app-dark.png

# Back button closes the location popup instead of leaving the app
adb shell cmd uimode night no
launch
sleep 10
adb shell "am start -n $PKG/.MainActivity --ez $PKG.OPEN_LOCATION true" > /dev/null
sleep 2
adb exec-out screencap -p > screens/location-popup.png
adb shell input keyevent KEYCODE_BACK
sleep 1
adb exec-out screencap -p > screens/after-back.png

# The app must still be running (no crash)
adb shell pidof "$PKG"
