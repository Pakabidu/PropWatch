# PropWatch — HF, 6m, 2m & 70cm DX alerts

An Android app that runs the same checks as your scheduled task every few hours in the background, and only notifies you when conditions are very good.

## What's new in v5.6

- **Third cabinet: WW2 aircraft wireless** (Settings → Cabinet), modelled on the RAF bomber receiver and transmitter pair (original artwork; type and serial markings are PropWatch's own):
  - Black stove-enamel panel with slotted screws, chrome grab handles and engraved brass label plates.
  - Two square brass-bezel meters (**signal strength** and **Kp index**) with a green **tuning eye** between them whose dark wedge closes as conditions open.
  - A cream semicircular dial with colour-coded HF, 6m, 2m and 70cm scales and a condition scale, read by a hairline pointer. The big knurled knob below it runs **Check now** and turns while checking.
  - A **station record** card (locator, build, type), colour-coded **lever switches** for SOUND (blue), MONITOR (red) and GPS (yellow), and a **band switch** with coloured sectors between the red AERIAL and yellow ANODE knobs (those two are for show).
  - Brass-plate headings, a ruled white log card and a navigator's chart for the station log.
- **Check my location has its own sound** in every cabinet: a direction-finding sweep through the null, then "QTH?" (what is your location?) in Morse.

## What's new in v5.5

- The automatic check when the app opens now plays the same valve warm-up sound as Check now, as the dial lamps warm up.

## What's new in v5.4

- **Checks on every start.** Opening the app runs a fresh propagation check straight away, quietly.
- **Sound moved to Check now.** The valve warm-up sound no longer plays when the app opens. It plays when you press **Check now** (or the receiver's vernier knob), as the set tunes in.

## What's new in v5.3

- **Second cabinet: Communications receiver.** Settings → Cabinet switches between the 1940s portable and a 1940s communications receiver, modelled closely on the classic ham-shack receivers (original artwork; no maker's names or logos):
  - A grey crackle speaker cabinet with chrome louvres on top; a black crackle front panel with chrome art-deco ribs on the sides.
  - An amber backlit main dial with stacked HF, 6m, 2m and 70cm scales and a condition scale, read by a hairline pointer. An **S-meter** reads S3 when quiet, S7 for a heads-up and S9+ when a band is open; a **Kp meter** shows the latest planetary K index from NOAA.
  - Red-chevron nameplate. The gold-ringed vernier knob is **Check now** (its ring turns while checking); the rotary **waveband switch** picks the band, with a pilot lamp by each band that's open or worth a look; the toggles are **SOUND**, **MONITOR** and **GPS**. TONE, SELECTIVITY and BAND SPREAD are for show.
  - Below the panel, the log chart and station-log map are backlit amber and the lettering is white, with red-chevron headings.

## What's new in v5.2

- **Bigger lettering everywhere.** Text on the dial, plates, log chart and station log is about 30% larger, and now also follows the phone's font-size setting (up to 1.5x), with the layout growing to fit. Readings, sources, settings and buttons use larger, higher-contrast type too.
- Every donation amount has its own thank-you line.

## What's new in v5.1

- **Support PropWatch** in the lid: a cheeky ask to stand the operator a £1 coffee. Tap it to choose any amount from £1 to £10 on a slider (each of the ten amounts has its own thank-you line), then **Donate with PayPal** opens PayPal's donation page in the browser with the amount and GBP filled in. Donations go to the PayPal account set in `Donate.kt`. Entirely optional: nothing in the app is locked behind it.

## What's new in v5.0

A full visual rework modelled closely on a 1940s portable shortwave set (original artwork; no maker's names or logos):

- **Case:** black crackle-finish leatherette with worn brown cracks, a crazed leather handle on aged copper brackets, and copper latches at the corners.
- **Front panel:** plain black basket-weave speaker grille in a bevelled recess, beside an etched chrome **tone plate** whose rocker switches really work: **SOUND** (switch sounds), **MONITOR** (background checks) and **GPS** (phone location). They replace the old Settings switches and take effect at once.
- **Dial:** smoky glass in a chrome bezel. The needle pivots from the centre over gold HF, 6m, 2m and 70cm scales to QUIET, HEADS-UP or OPEN. A gold horseshoe "megacycles" scale curves round below, with the PropWatch script either side.
- **Waveband plate:** etched chrome with a black push-button per band. Beside each button, like the radio's "best reception" chart, is that band's live condition with a pilot lamp: OPEN, HEADS-UP, QUIET or NO DATA. Pressing a button still filters the readings.
- **Lid log chart:** cream graph paper with a row per band and the last 24 hours across. Every check is plotted: green open, amber heads-up, a dash for quiet, a red cross for no data.
- **Lid panels:** readings are boxed like the operating instructions, with cream headers, and the sources are a ruled table you tap to open.
- **App icon:** the set front-on: crackled case, leather handle on copper brackets, grille, smoky dial with gold horseshoe, and chrome band buttons.

## What's new in v4.0

A new look inspired by 1940s portable shortwave sets (original artwork; no maker's branding):

- **Case:** black alligator-grain leatherette with a stitched leather carrying handle on chrome brackets.
- **Speaker cloth:** dark woven grille with a stitched cream Yagi-and-radio-waves motif and a gold script nameplate.
- **Dial:** a square black dial in a chrome bezel with gold arc scales for HF, 6m, 2m and 70cm. The cream needle swings from a chrome pivot to QUIET, HEADS-UP or OPEN, and the dial lamp warms up at startup.
- **Waveband switch:** a brushed-chrome plate of black push-buttons (ALL, HF, 6 M, 2 M, 70 CM). They work: pressing one shows only that band's readings, and the needle and status follow that band.
- **Lid:** readings, sources and settings sit on a black instruction panel with a double cream rule and corner screws, under cream nameplate headings.
- **Station log:** the location screen in Settings is now a cream-on-black world chart with your station pinned in red, above your town, country, coordinates, locator and fix details. **Check my location** dims the chart and sweeps a tuning cursor across it while the fix comes in.
- **App icon:** the portable set itself, with its leather handle, gold-scaled dial and chrome waveband buttons.

## What's new in v3.2

- The lit radio location screen now lives in **Settings** instead of popping up. It always shows the station's town, country, latitude/longitude and Maidenhead locator; **Check my location** dims it, warms it up and sweeps the tuning cursor in place while a fresh fix comes in. If location is switched off, a **Turn on location** button appears under it.
- New WW2-style app icon: an olive-drab field receiver with a leather handle, backlit dial and red needle, speaker slots, bakelite knobs and a green pilot lamp. Includes a single-colour version for Android 13+ themed icons.

## What's new in v3.1

- **Phone location.** The station position now comes from the phone's GPS (or network location) by default, so bearings, the HF day/night choice and the settings follow you. Turn **Use phone location (GPS)** off to type coordinates instead. Settings shows the station's town, country and Maidenhead locator.
- **Check my location** button: an old-radio tuning screen warms up, sweeps while the fix comes in, then shows your town, country, latitude/longitude, Maidenhead locator and fix accuracy.
- **Fixes HTTP 429 on live spots.** The 6m, 2m and 70cm checks now share one PSKReporter query (6m–70cm), reused for 5 minutes, so repeated "Check now" taps don't hit the server again. If PSKReporter does rate-limit, the app waits at least 15 minutes (or as long as the server asks) and keeps showing the last results meanwhile.
- Location is only read while the app is open; background checks use the last position. Town names come from the phone's geocoder, or OpenStreetMap if the phone has none. The North Sea tropo paths stay fixed, as they're specific to eastern England.

## What's new in v3

- **More bands.** HF band conditions (80m–40m, 30m–20m, 17m–15m, 12m–10m, day or night for your station) from N0NBH, with a heads-up when 10m/12m are rated Good. Live **6m** monitoring from PSKReporter alerts on sporadic E and on long-haul (≥3500 km) multi-hop / F2 DX, and shows N0NBH's Europe 6m and 4m Es status. The dial gains an HF scale marking 160, 80, 40, 20, 15, 10 and 6m.
- **Check for updates** button in Settings. It compares your build number with the latest published build and offers to download the new APK. The build number is shown under the button.
- Every build is now signed with the same key, so updates install over the old version. **One-time step:** v1/v2 were signed with a throwaway key, so uninstall that version before installing v3 (settings reset once). After that, updates install normally.

## What's new in v2

- 1940s portable-receiver look: black leatherette case, chrome louvre grille, brass trim, and a backlit cream slide-rule dial (2m and 70cm scales) whose red needle points to QUIET, HEADS-UP or OPEN. On startup the dial lamp warms up and the needle tunes in, in time with the startup sound. Findings show as pilot lamps (green = alert, amber = heads-up, red = couldn't check).
- WW2-era radio sounds: bakelite toggle snap on main buttons, rotary detent on link buttons, and a valve-set warm-up (hum, tuning sweep, CW "PW") when you press Check now (from v5.4; it used to play on startup). All sounds are original, synthesised for PropWatch.
- "Radio switch sounds" on/off switch in Settings (takes effect instantly).

## What it checks

| Mode | Source | Alert when |
|---|---|---|
| Aurora | NOAA SWPC Kp observed + 3-day forecast, SWPC watches | Kp ≥ 6- now or within 24 h, or a G2+ watch |
| Tropo (forecast) | Open-Meteo vertical profiles at 5 points from the Wash to Denmark | Refractivity gradient ≤ −157 N/km (ducting) on both sides of the North Sea for ≥ 2 forecast hours |
| Tropo / Es (live) | PSKReporter spots, last 30 min | 2m: ≥ 5 station pairs ≥ 700 km touching UK/Benelux, or ≥ 2 pairs ≥ 1000 km. 70cm: ≥ 2 pairs ≥ 700 km. Es: ≥ 2 pairs 1000–2500 km in NW Europe, May–Aug or December |
| HF bands | N0NBH (hamqsl.com) solar data | Shown for reference; heads-up when 12m–10m is rated Good for the current day/night |
| Live 6m | PSKReporter spots, last 30 min | Es: ≥ 3 station pairs 700–2500 km in NW Europe. DX: ≥ 2 pairs ≥ 3500 km touching UK/Benelux |
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

## Updating

Tap **Check for updates** in Settings. If a newer build is published it offers **Download**; open the downloaded `PropWatch.apk` and install over the current version. Settings are kept.

Only builds from the `main` branch are published as updates. The APK is signed with the debug key in `app/debug.keystore`, which is committed so every build matches. That's fine for a personal app, but it means anyone with the repo could sign an APK that installs over PropWatch, so for wider distribution switch to a private release key kept in a GitHub secret.

## Installing

Open `PropWatch.apk` on the phone and allow "Install unknown apps" for your browser or file manager when asked. On first launch, allow notifications, then tap **Send test notification** to confirm they come through.

To make alerts reliable, set the app's battery usage to **Unrestricted** (Settings → Apps → PropWatch → Battery).

## Defaults

Station: the phone's location (allow location access when asked). Until a fix arrives, or with GPS turned off, it's Boston, Lincolnshire (52.98 N, 0.02 W). Checks every 3 hours on any network. All of this can be changed in the app's settings section.
