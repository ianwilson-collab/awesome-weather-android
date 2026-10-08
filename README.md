# AWE Weather

**Awesome Weather for Android**: the [Awesome Weather web app](https://github.com/ianwilson-collab/awesome-weather) as an installable Android app, with two home-screen widgets built in.

- **The app** is the web app's `index.html`, bundled unchanged inside the APK, so it opens instantly and works offline with the last saved forecast.
- **Current weather widget (2×1):** temperature, conditions, place and update time.
- **Forecast widget (4×2):** current conditions plus 5 days. Stretch it wider and it shows 7 days.
- **Widget settings** (gear button on each widget): background transparency with a live preview, and refresh every 30 minutes, every 60 minutes, or only when you tap refresh.
- **Location:** the widgets follow the place saved in the app.

Forecasts come from [Open-Meteo](https://open-meteo.com) (free, no key). The app also uses the U.S. National Weather Service, as the web app does.

Requires Android 12 or newer.

## Installing on a phone

1. Open the [Releases page](https://github.com/ianwilson-collab/awesome-weather-android/releases) in Chrome on the phone.
2. Tap the `AWE-v….apk` file under the latest release, then **Open**.
3. The first time, Android asks to allow Chrome to install apps. Allow it, then tap **Install**.

New versions install over the old one and keep your location and widgets.

## How builds work

Every push to `main` runs [`.github/workflows/build.yml`](.github/workflows/build.yml):

1. **Unit tests**, then a **signed APK** (named `AWE-v<version>.apk`).
2. **On-device checks** on an Android 14 emulator: draws every widget variation, checks the home-screen views load, and screenshots the real app (light, dark, loading screen, location popup). The pictures are saved with the run as `screenshots` and on the `ci-screens` branch.
3. When `versionName` is new, the APK is also **published** on the Releases page (as `v<version>`).

## Updating the web app inside the APK

1. Copy the new `index.html` from the web app over `app/src/main/assets/web/index.html`.
2. Raise `versionCode` (by 1) and `versionName` in `app/build.gradle.kts`.
3. Push. Once the checks pass, the new version appears on the Releases page.

## Signing key

The APK is signed with a permanent key so updates install over each other. The key lives only in this repo's GitHub **secrets** (`AWE_KEYSTORE_BASE64`, `AWE_KEYSTORE_PASSWORD`, `AWE_KEY_ALIAS`, `AWE_KEY_PASSWORD`) and in the owner's private backup. It is never committed. If it's lost, future versions can't install over the current one.

## Project layout

| Path | What it is |
|---|---|
| `app/src/main/assets/web/index.html` | The web app, unchanged |
| `app/src/main/java/com/prostellis/awe/MainActivity.kt` | The app screen: web view, location link to the widgets, back button, location permission |
| `app/src/main/java/com/prostellis/awe/weather/` | Forecast download format and the web app's logic (day icons, overnight lows, sky and temperature colors) |
| `app/src/main/java/com/prostellis/awe/widget/` | Widget drawing, settings screen and background refresh |
| `app/src/test/` | Unit tests |
| `app/src/androidTest/` | On-device tests that draw the widgets |

DM Sans font: SIL Open Font License, see [`licenses/DM_Sans_OFL.txt`](licenses/DM_Sans_OFL.txt).
