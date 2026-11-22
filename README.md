# PropWatch 1.0 — DX propagation alerts for radio amateurs

PropWatch watches the bands for you and only speaks up when conditions are worth it: tropospheric ducting, sporadic E, aurora, long-haul 6m DX, meteor showers and good HF conditions. It runs its checks in the background every few hours and every time you open it, and notifies you when an opening is on.

It looks and sounds like a well-used 1940s portable shortwave set: black alligator-grain case with a leather handle, a black dial with gold scales, chrome plates, bakelite switches and jewel lamps, all scuffed, scratched and dusty from decades on the shack bench.

## On the set

- **The dial** points to QUIET, HEADS-UP or OPEN for the chosen waveband; its lamp warms up as the set comes on.
- **Waveband buttons** (ALL, HF, 6 M, 2 M, 70 CM) filter the readings, with a pilot lamp showing each band's condition.
- **Rocker switches:** **SOUND** (all the set's sounds, including the broadcast radio), **MONITOR** (background checks) and **GPS** (use the phone's location).
- **MOOD LIGHT** knob: the room goes dark around a table lamp and every dial, chart and jewel lamp glows warm orange.
- **The little broadcast set** under the speaker plays old-time radio: **1** The 1920s Radio Network, **2** Old Time Radio Service, **3** your own station (the first press asks for a stream address; long-press to change it). It keeps playing in the background with a Stop button in its notification.
- **Check now** runs every check straight away, to the sound of the valves warming up. A check also runs every time the app opens.

## In the lid

- **Station log:** a world chart with your station pinned, your town, coordinates and Maidenhead locator. **Check my location** takes a fresh fix (with a direction-finding sweep and "QTH?" in Morse).
- **DX log:** the last 24 hours of checks for each band on a lit chart card.
- **Readings**, **Sources** (links to the maps and forecasts behind the checks), **Tropo bands**, **Settings** and **Support PropWatch**.

## What it checks

| Reading | Source | Alert when |
|---|---|---|
| Tropo forecast | Open-Meteo vertical profiles at your station and 250/500 km out on 8 bearings | A duct (refractivity gradient ≤ −157 N/km) runs continuously out from you along a bearing for ≥ 2 forecast hours |
| Live spots, per tropo band | PSKReporter reception reports, last 30 min, in each band's weak-signal segment | A cluster of long paths touching your area (within 800 km of you), with thresholds per band — see below |
| Aurora | NOAA SWPC Kp observed and forecast, SWPC watches | Kp ≥ 6- now or within 24 h, or a G2+ watch |
| HF bands | N0NBH (hamqsl.com) | Shown for reference; heads-up when 12m–10m are rated Good |
| Meteor scatter | Built-in shower dates | Quadrantids, Perseids or Geminids peak within 24 h (heads-up) |

Live-spot thresholds:

| Band | Opening when |
|---|---|
| 6m | ≥ 3 sporadic-E paths 700–2500 km in your area, or ≥ 2 paths over 3500 km (multi-hop / F2) |
| 4m | ≥ 2 Es paths 900–2500 km, or ≥ 2 tropo paths over 500 km (or 1 over 800 km) |
| 2m | ≥ 5 paths over 700 km (or 2 over 1000 km), or sporadic E in season |
| 1.25m, 70cm, 23cm | ≥ 2 paths over 600–700 km, or one very long path |
| Microwaves | 1 path over 400 km |

2m needs a cluster because aircraft scatter on FT8 regularly reaches 700–800 km. Meteor-scatter and EME modes (MSK144, Q65, JT65…) and the QO-100 satellite are left out. Each opening notifies when it first appears, then at most every 12 hours while it lasts. Thresholds live in `SpotRules.kt`.

### Tropo bands

Settings → Tropo bands lists 6m, 4m, 2m, 1.25m, 70cm and 23cm, plus an optional microwave group (13cm, 9cm, 6cm, 3cm). Defaults follow your ITU region, worked out from your locator (Region 1 gets 4m, Region 2 gets 1.25m, microwaves are opt-in); switch any band on or off. Each band carries a duct sensitivity factor relative to 2m (higher bands are trapped by shallower ducts), which the forecast uses to name the bands a duct favours.

### Limits worth knowing

- The tropo forecast is computed from weather-model profiles, not Hepburn's index. Model levels are ~200 m apart, which smooths out thin ducts, so it errs towards missing a weak lift rather than crying wolf. The Hepburn link opens his maps for a visual check.
- Live detection relies on digital-mode stations reporting to PSKReporter; a CW/SSB-only opening won't show.
- Android may delay background checks when the phone is idle. **Check now** always runs immediately.

## Installing

1. On the phone, open the repo's **Releases → PropWatch (latest build)** and download `PropWatch.apk`.
2. Open it and allow "Install unknown apps" for your browser or file manager when asked.
3. On first launch allow notifications and location, then tap **Send test notification** to confirm alerts come through.

For reliable alerts, set the app's battery usage to **Unrestricted** (Settings → Apps → PropWatch → Battery).

## Updating

PropWatch checks for a newer build every time it starts (and when you tap **Check for updates**), downloads it in the background and offers to install it. Android always asks you to confirm, and the first time asks you to allow PropWatch to install apps. Settings are kept.

## Defaults

Station: the phone's location. Until a fix arrives, or with GPS off, it's Boston, Lincolnshire (52.98 N, 0.02 W). Checks every 3 hours on any network. All of this can be changed in Settings.

## Building

GitHub Actions (`.github/workflows/build.yml`) runs the unit tests and builds the APK on every push; builds from `main` are published as the "latest" release, with `version.txt` for the in-app updater. Or open the project in Android Studio and **Build → Build APK(s)**.

The APK is signed with the debug key in `app/debug.keystore`, committed so every build installs over the last. That's fine for a personal app; for wider distribution, switch to a private release key kept in a GitHub secret.
