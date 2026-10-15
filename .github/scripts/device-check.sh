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

# Real screenshots of the signed APK. The emulator draws in software and sometimes goes down partway through;
# when that happens it's noted as a warning, since the on-device tests above are what gate a release.
# Anything else, like the app crashing, still fails the check.
if ! bash .github/scripts/device-screens.sh; then
  if adb get-state > /dev/null 2>&1; then exit 1; fi
  echo "::warning title=Screenshots::The emulator went down during the screenshot pass. The on-device tests passed."
fi
