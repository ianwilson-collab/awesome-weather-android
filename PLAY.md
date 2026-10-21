# Google Play: answers for the Play Console

Drafted answers for putting AWE Weather on Google Play through a personal developer account's **Internal testing** track. Copy them into the Play Console as you go. Items marked **Check** are best readings of Google's questions; read them over before submitting.

## Files and links

- **Upload:** `AWE-vX.Y.aab` from the matching release on the [Releases page](https://github.com/ianwilson-collab/awesome-weather-android/releases). It's signed with the same key as the `.apk`; Play keeps that as your upload key.
- **Privacy policy URL:** https://ianwilson-collab.github.io/awesome-weather/privacy.html
- **App name:** AWE Weather
- **Package name:** com.prostellis.awe (set by the upload; it can't change later)

## Main store listing

Internal testers see this listing, so it's worth filling in even before a public launch.

- **Short description (80 characters max):** Your forecast, beautifully told. Animated skies, home-screen widgets and a clock.
- **Full description:**

  AWE Weather shows today and the next 16 days at a glance, over a sky that follows the real weather and time of day.

  • Day cards with highs, lows, rain, wind, sunrise and sunset
  • Hourly Forecast for 7 days, with a rain lane
  • Extended Forecast out to 16 days
  • Plan a Date: what's typical on any day of the year
  • Stickers for holidays, meteor showers, eclipses, aurora nights, full moons and first frost
  • 5 sky styles and 3 icon styles (Basic, Fine Outline, 3D)
  • Widgets from 1×1 to 5×2, plus AWE Clock with 10 styles and 40 colors
  • Pull down to refresh

  No accounts, no ads, no tracking. Weather from Open-Meteo, the U.S. National Weather Service and NOAA.

- **App icon:** 512 × 512 PNG (from the app's launcher icon).
- **Feature graphic:** 1024 × 500 PNG (can be cut from the one-page infographic).
- **Phone screenshots:** at least 2 (the CI run saves app pictures on the `ci-screens` branch).
- **Category:** Weather
- **Contact email:** required by Play; use whichever address you want testers to see.

## App content (Policy → App content)

| Form | Answer |
|---|---|
| Privacy policy | The URL above |
| Ads | No, the app doesn't contain ads |
| App access | All functionality is available without special access (no sign-in) |
| Content rating | See below; expected result: Everyone / PEGI 3 |
| Target audience | 13–15, 16–17 and 18 and over (not under 13, which keeps the app out of the Families program) |
| News app | No |
| Government app | No |
| Financial features | None |
| Health app | No |
| Data safety | See below |

### Content rating questionnaire

- **Category:** All Other App Types
- Violence, sexuality, language, controlled substances, gambling: **No** to all
- Users can interact or exchange content: **No**
- Shares the user's location with other users: **No**
- Digital purchases: **No**
- Unrestricted internet access (a web browser or search engine): **No**

### Data safety

- **Does your app collect or share any of the required user data types?** Yes
- **Is all of the user data collected by your app encrypted in transit?** Yes (every request uses HTTPS)
- **Do you provide a way for users to request that their data is deleted?** No. Nothing is kept anywhere but the phone; clearing the app's storage or uninstalling removes it. (**Check:** if Play insists, answer that data is deleted when the app is uninstalled.)
- **Data types:** Location → **Approximate location**
  - **Collected:** Yes. The place's coordinates go to the weather services to get its forecast.
  - **Shared:** No. **Check:** the coordinates go straight from the phone to the weather services, which act as the app's service providers, and Play doesn't count that as sharing. Answer Yes here if you'd rather be cautious.
  - **Processed ephemerally:** Yes
  - **Required or optional:** Optional (a city can be typed instead)
  - **Purpose:** App functionality
- **Everything else** (personal info, financial info, health, messages, photos, audio, files, calendar, contacts, app activity, web browsing, device IDs): **Not collected**

### Permission declarations

- **Alarms and reminders (`SCHEDULE_EXACT_ALARM`):** asked for by the user from Clock settings ("Keep the clock on the minute") so the clock widget changes right at each minute. No Play declaration form is needed for this permission; the restricted one (`USE_EXACT_ALARM`) was removed in v2.3.
- **Approximate location (`ACCESS_COARSE_LOCATION`):** foreground only, when the user taps "Use my location". No background location.

## Internal testing release

1. Testing → **Internal testing** → **Create new release**.
2. Upload the `.aab`. Release name: the version (for example `2.3`). Release notes: a line or two of what's new.
3. **Testers** tab → create an email list with each tester's Google account → copy the **join link**.
4. Save, review and **Start rollout to Internal testing**. Internal releases skip the full review and are usually ready within minutes.
5. On the phone: open the join link, tap **Accept**, then **Download it on Google Play**.

**Before installing from Play:** uninstall the GitHub version first. Google Play signs the app with its own key, so Android treats the two as different apps. Then place the widgets again and redo their settings.
