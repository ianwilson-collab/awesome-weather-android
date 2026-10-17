# AWE Weather

**Awesome Weather for Android**: the [Awesome Weather web app](https://github.com/ianwilson-collab/awesome-weather) as an installable Android app, with four home-screen widgets built in.

- **The app** is the web app's `index.html`, bundled unchanged inside the APK, so it opens instantly and works offline with the last saved forecast.
- **AWE 1×1:** the temperature, centered on a square tinted by the weather, with the weather icon as a faint watermark.
- **AWE Daily (2×1 to 5×1):** the current weather; stretched wider, it adds today and tomorrow (3×1), five days (4×1) or six days (5×1), each on its own tile with its high and low.
- **AWE Hourly (2×1 to 5×1):** the current weather plus the next 1, 3, 5 or 7 hours, starting at the next whole hour.
- **AWE Forecast widget (4×2):** current conditions plus 5 days; 7 days when it's wide enough. Each day shows its icon, high and low with a temperature bar, and the rain amount. Text and icons grow to fill a larger widget.
- **Widget settings:** one settings sheet for every widget. It opens when a widget is placed, by long-pressing a widget, and from the forecast widget's gear button: location, icon style, temperature color (the Clock's color picker: custom, 16 solids, 12 metallics, 12 gradients; on the forecast widget it can color the day names too), transparency with a live preview, show or hide the current weather (forecast widget), and refresh every 30 minutes, every 60 minutes, or only when you tap refresh. Every widget has a small refresh button in its top-right corner; the forecast widget also has the gear. No app labels under the widgets.
- **Icon style:** Basic, Glow (the default) or Fine Outline weather symbols. One choice, made in any widget's settings or the app's settings button (top-right), applies to the app and every widget.
- **Background and animation style:** Basic, Painterly, Dynamic (the default), Illustration or Mt Hood, chosen in the app's settings button (top-right). It changes the sky behind the app, the art inside each day card, and how they move. The widgets' sky follows it as a still picture (the 1×1 keeps its weather tint).
- **Event days:** holidays, first and last frost, the first day of each season, meteor showers, eclipses you can see from your place, nights when the aurora may reach you, and new and full moons. The app shows them as colored stickers and short lines on the day cards; the forecast widget marks them with a small dot in the same color under the day name.
- **Location:** the widgets follow the place saved in the app.

Forecasts come from [Open-Meteo](https://open-meteo.com) (free, no key). The app also uses the U.S. National Weather Service and NOAA's space weather (Kp) forecast for auroras, as the web app does.

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
2. If a background style changed, redraw the widget sky pictures: `pip install playwright pillow && python3 tools/widget_skies.py`.
3. Raise `versionCode` (by 1) and `versionName` in `app/build.gradle.kts`.
4. Push. Once the checks pass, the new version appears on the Releases page.

## Signing key

The APK is signed with a permanent key so updates install over each other. The key lives only in this repo's GitHub **secrets** (`AWE_KEYSTORE_BASE64`, `AWE_KEYSTORE_PASSWORD`, `AWE_KEY_ALIAS`, `AWE_KEY_PASSWORD`) and in the owner's private backup. It is never committed. If it's lost, future versions can't install over the current one.

## Project layout

| Path | What it is |
|---|---|
| `app/src/main/assets/web/index.html` | The web app, unchanged |
| `app/src/main/java/com/prostellis/awe/MainActivity.kt` | The app screen: web view, location link to the widgets, back button, location permission |
| `app/src/main/java/com/prostellis/awe/weather/` | Forecast download format and the web app's logic (day icons, overnight lows, sky and temperature colors) |
| `app/src/main/java/com/prostellis/awe/widget/` | Widget drawing, settings screen and background refresh |
| `app/src/main/assets/widget-sky/` | The widget sky pictures for each background style, drawn from the web app by `tools/widget_skies.py` |
| `app/src/test/` | Unit tests |
| `app/src/androidTest/` | On-device tests that draw the widgets |

DM Sans font: SIL Open Font License, see [`licenses/DM_Sans_OFL.txt`](licenses/DM_Sans_OFL.txt).
