# PropWatch — 2m / 70cm DX alerts

An Android app that runs the same checks as your scheduled task every few hours in the background, and only notifies you when conditions are very good.

## What's new in v2

- 1940s portable-receiver look: black leatherette case, chrome louvre grille, brass trim, and a backlit cream slide-rule dial (2m and 70cm scales) whose red needle points to QUIET, HEADS-UP or OPEN. On startup the dial lamp warms up and the needle tunes in, in time with the startup sound. Findings show as pilot lamps (green = alert, amber = heads-up, red = couldn't check).
- WW2-era radio sounds: bakelite toggle snap on main buttons, rotary detent on link buttons, and a valve-set warm-up (hum, tuning sweep, CW "PW") on startup. All sounds are original, synthesised for PropWatch.
- "Radio switch sounds" on/off switch in Settings (takes effect instantly).

## What it checks

| Mode | Source | Alert when |
|---|---|---|
| Aurora | NOAA SWPC Kp observed + 3-day forecast, SWPC watches | Kp ≥ 6- now or within 24 h, or a G2+ watch |
| Tropo (forecast) | Open-Meteo vertical profiles at 5 points from the Wash to Denmark | Refractivity gradient ≤ −157 N/km (ducting) on both sides of the North Sea for ≥ 2 forecast hours |
| Tropo / Es (live) | PSKReporter spots, last 30 min | 2m: ≥ 5 station pairs ≥ 700 km touching UK/Benelux, or ≥ 2 pairs ≥ 1000 km. 70cm: ≥ 2 pairs ≥ 700 km. Es: ≥ 2 pairs 1000–2500 km in NW Europe, May–Aug or December |
| Meteor scatter | Built-in shower dates | Quadrantids, Perseids or Geminids peak within 24 h (low-priority heads-up) |

Meteor-scatter and EME modes (MSK144, Q65, JT65…) are left out of the live tropo count. The 2m threshold is set above a single 700 km spot because aircraft scatter on FT8 regularly reaches 700–800 km from eastern England.

The same opening won't buzz you repeatedly: each mode notifies when it first crosses its threshold, then at most every 12 hours while it stays on.

### Limits worth knowing

- **Hepburn's maps are images**, so the app can't read his index. The tropo forecast is computed from weather-model profiles instead. Model pressure levels are ~200 m apart, which smooths out thin ducts, so it errs towards missing a weak lift rather than crying wolf. The Hepburn button opens his page for a visual check.
- DXView blocks automated access; it's a button in the app instead.
- Live detection relies on digital-mode stations reporting to PSKReporter. A CW/SSB-only opening won't show.
- Android may delay background checks by up to an hour or so when the phone is idle (Doze). "Check now" always runs immediately.

All thresholds are constants at the top of `Checker.kt` if you want to tune them.

## Getting the APK

The project is ready to build but not pre-built. Two ways:

### Option A — GitHub builds it for you (no software to install)

1. On a computer, create a free GitHub account and a new **empty** repository.
2. Unzip `PropWatch.zip` and, on the repo page, choose **Add file → Upload files**. Drag in the *contents* of the PropWatch folder (including the hidden `.github` folder — on a Mac press ⌘⇧. in Finder to show it). Commit.
3. Open the **Actions** tab; the "Build APK" job takes about 4 minutes.
4. On your phone, open the repo's **Releases → PropWatch (latest build)** and download `PropWatch.apk`.

### Option B — Android Studio

Open the folder in Android Studio (Hedgehog or newer), let it sync, then **Run** to a phone with USB debugging on, or **Build → Build APK(s)**.

## Installing

Open `PropWatch.apk` on the phone and allow "Install unknown apps" for your browser or file manager when asked. On first launch, allow notifications, then tap **Send test notification** to confirm they come through.

To make alerts reliable, set the app's battery usage to **Unrestricted** (Settings → Apps → PropWatch → Battery).

## Defaults

Station: Boston, Lincolnshire (52.98 N, 0.02 W). Checks every 3 hours on any network. Both can be changed on the app's settings section.
