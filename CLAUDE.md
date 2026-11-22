# PropWatch — notes for Claude

PropWatch is an Android app (Kotlin, views built in code, no layout XML) that watches
VHF/UHF/HF propagation for radio amateurs and alerts on openings.

## Design direction (always)

The whole app must feel like **warm, old radio equipment in used condition** — not just
"vintage", but a set that has lived on a shack bench for decades:

- **Warm light.** Dials, meters, jewel lamps and charts glow amber/orange like valve-era
  lamps behind old glass. Lit things should look lit; prefer warm wattage over cold white.
  Anything lit implements `MoodGlow` so it glows under the mood light.
- **Used, not new.** Use the `Wear` helpers (scratches, scuffs, grime, dust, chips,
  rubbed lettering, rust, coffee rings) and `WornDrawable` on new panels and buttons.
  Chrome wears through to brass, plating to bare metal, paint chips at edges.
- **Period materials.** Black alligator leatherette, bakelite, etched/mottled chrome
  (`Hardware.plate`), slotted screws, brass/copper hardware, cream engraved lettering,
  gold dial scales, faceted jewel lamps (`Hardware.jewel`).
- **Readable for older eyes.** Custom-drawn text uses the `t` text unit (1.3–1.5× and
  follows the system font scale); TextViews 13sp and up. Long labels shrink with
  `fitText` rather than overflow.
- **Restraint.** Add detail where a real set would have it; don't clutter.
- One cabinet only: the 1940s portable (styled after the classic portable shortwave
  sets, without any maker's names or logos).

## Working on it

- No Android SDK in the cloud container: the app can't be built locally. CI
  (`.github/workflows/build.yml`) runs unit tests and builds the APK on every push;
  watch it. Only builds from `main` are published as releases (with `version.txt`),
  which the in-app updater reads.
- Pure-Kotlin modules (`TropoBands`, `SpotRules`, `TropoPaths`, `Geo`) have JVM tests in
  `app/src/test`; they can be run locally with a plain Kotlin/JVM Gradle project.
- Preview custom-drawn UI with a browser canvas mock (same geometry) before pushing.
- Watch for name clashes in the single `uk.dx.propwatch` package (e.g. `Station`).
- Versioning: `versionName` stays "1.0" until the user says otherwise; only the build
  number (`versionCode` = CI run number) changes.
