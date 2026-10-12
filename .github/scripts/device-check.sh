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
adb logcat -c
# Keep Android's full log as it happens, so it survives if the emulator goes down (trimmed copy below)
adb logcat -v time > screens/logcat-live.txt 2>&1 &
adb shell cmd alarm set-timezone America/Los_Angeles || true

# Taps, swipes and keys. The animated page keeps the emulator's software graphics busy, so an input command
# can time out; try again once, then note it and carry on (the screenshots show what happened).
send() {
  adb shell input "$@" || { sleep 3; adb shell input "$@"; } || echo "::warning title=Device check::input $* did not go through"
}

launch() {
  adb shell am force-stop "$PKG"
  adb shell am start -n "$PKG/.MainActivity" > /dev/null
}

# Warm-up: a freshly started emulator restarts Google Play services in its first minutes, which closes any app using it
adb shell cmd uimode night no
launch
sleep 45

# Loading screen (captured right after launch), then the app once the forecast has loaded
launch
sleep 0.4
adb exec-out screencap -p > screens/loading-screen.png
sleep 14
adb exec-out screencap -p > screens/app-light.png

read -r W H < <(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr x ' ')
settings() { send tap $((W * 89 / 100)) $((H * 10 / 100)); sleep 2; }
tabs() { for _ in $(seq "$1"); do send keyevent KEYCODE_TAB; done; }

# Settings button (top-right): a new install starts on Glow icons and the Dynamic background.
# The page is always animating, so the accessibility tree never settles; tap the button's spot and pick with the keyboard.
# Tab order: the icon styles (Basic, Glow, Fine Outline), then the backgrounds (Basic, Painterly, Dynamic, Illustration, Mt Hood).
# Switch to Fine Outline icons and the Basic background: the emulator draws in software (no GPU), and scrolling the
# animated Dynamic page takes it down, so the checks that scroll run on Basic.
settings
adb exec-out screencap -p > screens/settings-popup.png
tabs 3
send keyevent KEYCODE_ENTER
sleep 1
adb exec-out screencap -p > screens/settings-fine-outline.png
tabs 1
send keyevent KEYCODE_ENTER
sleep 1
send keyevent KEYCODE_BACK
sleep 2
adb exec-out screencap -p > screens/app-basic-fine-outline.png

# Scroll to the bottom to show the footer line
for _ in 1 2 3 4 5 6; do send swipe $((W / 2)) $((H * 85 / 100)) $((W / 2)) $((H * 15 / 100)) 250; done
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
send keyevent KEYCODE_BACK
sleep 1
adb exec-out screencap -p > screens/after-back.png

# Mt Hood background (the eighth stop), last, with no scrolling
settings
tabs 8
send keyevent KEYCODE_ENTER
sleep 2
adb exec-out screencap -p > screens/settings-mthood.png
send keyevent KEYCODE_BACK
sleep 10
adb exec-out screencap -p > screens/app-mthood.png

# NOAA's aurora forecast must allow the app's page to read it (CORS); recorded, not required
curl -s -D - -o /dev/null -H "Origin: https://appassets.androidplatform.net" \
  https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json | grep -i "^access-control" > screens/noaa-cors.txt \
  || echo "No CORS header from NOAA: the aurora line won't show" > screens/noaa-cors.txt

# Android's log for the app (crashes, web view errors), kept with the screenshots
adb logcat -d -v time | grep -E "AndroidRuntime|FATAL|prostellis|chromium|ActivityTaskManager|ActivityManager: (Force|Process|Killing)|WebView|ANR" > screens/logcat.txt || true

# The app must still be running (no crash)
adb shell pidof "$PKG"
