package uk.dx.propwatch

import android.app.Activity
import android.app.AlertDialog
import android.location.Location
import android.provider.Settings
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Single screen, built in code so there's no layout XML to maintain. Styled as a 1940s portable receiver. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var checkBtn: Button
    private lateinit var dial: DialView
    private lateinit var bandStrip: BandStripView
    private lateinit var updateBtn: Button
    private lateinit var latField: EditText
    private lateinit var lonField: EditText
    private lateinit var screen: StationLogView
    private lateinit var locOnBtn: Button
    private var locating = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        Notifier.ensureChannels(this)
        val ask = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) ask += "android.permission.POST_NOTIFICATIONS"
        if (Store.useGps(this) && !Locator.hasPermission(this)) ask += LOCATION_PERMS
        if (ask.isNotEmpty() && savedInstanceState == null) requestPermissions(ask.toTypedArray(), REQ_STARTUP)
        PropWorker.schedule(this)
        Sfx.init(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        // Carrying handle, then the case front: speaker cloth, dial and band switch, readout, main button.
        root.addView(HandleView(this))
        val front = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(0x38, 0, 0, 0)); cornerRadius = dp(16).toFloat(); setStroke(dp(2), Color.BLACK)
            }
        }
        front.addView(ClothView(this))
        val tuning = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        dial = DialView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f)
        }
        tuning.addView(dial)
        bandStrip = BandStripView(this).apply {
            bands = BANDS.map { it.first to it.second }
            selected = BANDS.indexOfFirst { it.first == Store.band(this@MainActivity) }.coerceAtLeast(0)
            onSelect = { i ->
                Sfx.click(this@MainActivity)
                Store.saveBand(this@MainActivity, BANDS[i].first)
                render(Store.loadReport(this@MainActivity), animateNeedle = true)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = dp(10)
            }
        }
        tuning.addView(bandStrip)
        front.addView(tuning)
        status = TextView(this).apply {
            textSize = 12f; typeface = Radio.MONO; setTextColor(Radio.CREAM); alpha = 0.85f
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
        }
        front.addView(status)
        checkBtn = radioButton("Check now") { checkNow() }
        front.addView(checkBtn)
        root.addView(front)

        // The open lid: readings, sources and settings on a black instruction panel.
        val lid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(24), dp(26), dp(26))
            background = Radio.LidDrawable(this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
        }
        root.addView(lid)
        lid.addView(section("Readings", first = true))

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(4)) }
        lid.addView(results)

        lid.addView(section("Sources"))
        lid.addView(link("Hepburn tropo forecast", "https://www.dxinfocentre.com/tropo.html"))
        lid.addView(link("Live 144 MHz map (DXView)", "https://vhf.dxview.org/"))
        lid.addView(link("NOAA SWPC 3-day forecast", "https://services.swpc.noaa.gov/text/3-day-forecast.txt"))
        lid.addView(link("DXMaps (Es / tropo)", "https://www.dxmaps.com/spots/map.php?Lan=E&Frec=144"))
        lid.addView(link("DXMaps 6m", "https://www.dxmaps.com/spots/map.php?Lan=E&Frec=50"))
        lid.addView(link("HF band conditions (N0NBH)", "https://www.hamqsl.com/solar.html"))
        lid.addView(link("PSKReporter map (all bands)", "https://pskreporter.info/pskmap.html"))

        lid.addView(section("Settings"))
        val st = Store.station(this)
        val lat = number("Latitude", "%.4f".format(java.util.Locale.US, st.lat)).also { latField = it }
        val lon = number("Longitude", "%.4f".format(java.util.Locale.US, st.lon)).also { lonField = it }
        val interval = number("Check every (hours, 1–12)", Store.intervalHours(this).toString(), decimal = false)
        // The station's location, on a backlit radio screen that lives in Settings.
        screen = StationLogView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(8), 0, dp(6)) }
        }
        lid.addView(screen)
        locOnBtn = radioButton("Turn on location") {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }.apply { visibility = View.GONE }
        lid.addView(locOnBtn)
        lid.addView(radioSwitch("Use phone location (GPS)", Store.useGps(this)) { on ->
            Store.saveUseGps(this, on)   // applies immediately
            applyGpsMode()
            if (on) { if (Locator.hasPermission(this)) refreshStationQuietly() else requestPermissions(LOCATION_PERMS, REQ_REFRESH) }
        })
        lid.addView(radioButton("Check my location") { checkMyLocation() })
        lid.addView(label("Latitude")); lid.addView(lat)
        lid.addView(label("Longitude")); lid.addView(lon)
        lid.addView(label("Check every (hours, 1–12)")); lid.addView(interval)
        val enabled = radioSwitch("Background monitoring", Store.enabled(this)) { }
        val sounds = radioSwitch("Radio switch sounds", Store.sounds(this)) { on ->
            Store.saveSounds(this, on)   // applies immediately, no need to Save
        }
        lid.addView(enabled); lid.addView(sounds)
        lid.addView(radioButton("Save settings") {
            val la = lat.text.toString().toDoubleOrNull()
            val lo = lon.text.toString().toDoubleOrNull()
            val h = interval.text.toString().toIntOrNull()
            if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0 || h == null || h !in 1..12) {
                Toast.makeText(this, "Check the values", Toast.LENGTH_SHORT).show()
            } else {
                if (!Store.useGps(this)) {
                    val old = Store.station(this)
                    Store.saveStation(this, Station(la, lo))
                    if (Geo.km(old.lat, old.lon, la, lo) > 1.0) { Store.savePlace(this, null, null); nameStation(la, lo) }
                    showStation()
                }
                Store.saveInterval(this, h)
                Store.saveEnabled(this, enabled.isChecked)
                PropWorker.schedule(this)
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
        })
        lid.addView(radioButton("Send test notification") {
            Notifier.post(this, 99, Finding("Test", true, "PropWatch test",
                "Notifications are working. Real alerts look like this."))
        })
        updateBtn = radioButton("Check for updates") { checkForUpdate() }
        lid.addView(updateBtn)
        lid.addView(TextView(this).apply {
            textSize = 11f; typeface = Radio.MONO; setTextColor(Radio.BRASS); gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
            text = "PROPWATCH ${Updater.currentName(this@MainActivity)} · BUILD ${Updater.currentBuild(this@MainActivity)}"
        })
        lid.addView(TextView(this).apply {
            textSize = 12f; setTextColor(Radio.CREAM); alpha = 0.55f; setPadding(0, dp(16), 0, 0)
            text = "Alerts only fire when a threshold is met: tropo ducting forecast along the North Sea paths " +
                "or a live cluster of ≥700 km spots, Kp ≥ 6- (or a G2 watch), 2m or 6m Es in progress, 6m " +
                "long-haul DX, or a major meteor shower peak or 10m/12m opening (heads-ups). HF conditions for " +
                "80–10m are shown for reference. The tropo forecast is a model estimate, not Hepburn's index."
        })

        setContentView(ScrollView(this).apply {
            background = Radio.leatherette(this@MainActivity)
            isFillViewport = true
            addView(root)
        })

        applyGpsMode()
        if (Store.useGps(this) && Locator.hasPermission(this)) refreshStationQuietly()
        if (Store.placeCity(this) == null && !Store.useGps(this)) Store.station(this).let { nameStation(it.lat, it.lon) }

        val report = Store.loadReport(this)
        render(report, animateNeedle = false)
        if (savedInstanceState == null) {
            Sfx.startup(this)
            dial.warmUp(level(report))
        }
    }

    // ---- Styled widgets ----

    private fun radioButton(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        typeface = Radio.SERIF
        letterSpacing = 0.12f
        setTextColor(Radio.CREAM)
        background = Radio.buttonBg(this@MainActivity)
        stateListAnimator = null
        minHeight = dp(52)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(6), 0, dp(6)) }
        setOnClickListener { Sfx.click(this@MainActivity); onClick() }
    }

    private fun link(t: String, url: String) = Button(this).apply {
        text = "▸  $t"
        isAllCaps = false
        typeface = Radio.SERIF
        textSize = 14f
        setTextColor(Radio.CREAM)
        background = Radio.buttonBg(this@MainActivity, big = false)
        stateListAnimator = null
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(14), 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(4)) }
        setOnClickListener {
            Sfx.detent(this@MainActivity)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun radioSwitch(t: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = t
        typeface = Radio.SERIF
        setTextColor(Radio.CREAM)
        isChecked = checked
        val (thumb, track) = Radio.switchTints()
        thumbTintList = thumb; trackTintList = track
        setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, on -> onChange(on); Sfx.click(this@MainActivity) }
    }

    /** Section heading: a cream nameplate ribbon with widely spaced black capitals, as on the lid. */
    private fun section(t: String, first: Boolean = false) = TextView(this).apply {
        text = t.uppercase(); textSize = 12f; typeface = Radio.SERIF; letterSpacing = 0.45f
        setTextColor(Radio.INK); gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = GradientDrawable().apply { setColor(Radio.CREAM); cornerRadius = dp(2).toFloat() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, if (first) 0 else dp(22), 0, dp(8)) }
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 11f; typeface = Radio.MONO; setTextColor(Radio.BRASS); setPadding(0, dp(8), 0, 0)
    }

    private fun number(hint: String, value: String, decimal: Boolean = true) = EditText(this).apply {
        this.hint = hint
        setText(value)
        typeface = Radio.MONO
        setTextColor(Radio.CREAM)
        setHintTextColor(Color.argb(0x80, 0xF1, 0xE6, 0xC8))
        backgroundTintList = ColorStateList.valueOf(Radio.BRASS)
        inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_NUMBER
    }

    // ---- Behaviour ----

    private fun level(r: Report?): Int {
        val f = shown(r)
        return when {
            f.any { it.alert && !it.lowGrade } -> 2
            f.any { it.alert } -> 1
            else -> 0
        }
    }

    /** Which bands each reading is about, for the waveband buttons. */
    private fun bandsOf(mode: String): Set<String> = when (mode) {
        "HF bands" -> setOf("hf")
        "Live 6m spots" -> setOf("6m")
        "Live 2m spots" -> setOf("2m")
        "Live 70cm spots" -> setOf("70cm")
        "Tropo forecast" -> setOf("2m", "70cm")
        "Meteor scatter" -> setOf("6m", "2m")
        "Aurora" -> setOf("6m", "2m", "70cm")
        else -> setOf("hf", "6m", "2m", "70cm")
    }

    /** The readings for the selected waveband button. */
    private fun shown(r: Report?): List<Finding> {
        val band = Store.band(this)
        return r?.findings.orEmpty().filter { band == "all" || band in bandsOf(it.mode) }
    }

    private fun checkNow() {
        checkBtn.isEnabled = false
        checkBtn.text = "Tuning…"
        if (Store.useGps(this)) Locator.lastKnown(this)?.let { useFix(it) }
        val station = Store.station(this)
        Thread {
            val r = Checker(station).run()
            Store.saveReport(this, r)
            Notifier.handle(this, r)
            runOnUiThread {
                checkBtn.isEnabled = true
                checkBtn.text = "Check now"
                render(r, animateNeedle = true)
            }
        }.start()
    }

    // ---- Location ----

    companion object {
        /** Waveband buttons: key, button label, readout name. */
        private val BANDS = listOf(
            Triple("all", "ALL", "ALL BANDS"), Triple("hf", "HF", "HF"), Triple("6m", "6 M", "6 METRES"),
            Triple("2m", "2 M", "2 METRES"), Triple("70cm", "70 CM", "70 CM"))
        private const val REQ_STARTUP = 1
        private const val REQ_LOCATE = 2
        private const val REQ_REFRESH = 3
        private val LOCATION_PERMS = arrayOf(
            "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        val granted = Locator.hasPermission(this)
        showStation()
        if (granted) {
            when (code) {
                REQ_STARTUP, REQ_REFRESH -> if (Store.useGps(this)) refreshStationQuietly()
                REQ_LOCATE -> checkMyLocation()
            }
        } else if (code == REQ_LOCATE || code == REQ_REFRESH) {
            Toast.makeText(this, "Location permission refused — using the typed coordinates", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the system location settings: hide the prompt once location is on.
        if (::locOnBtn.isInitialized && locOnBtn.visibility == View.VISIBLE && Locator.enabled(this)) {
            locOnBtn.visibility = View.GONE
            showStation()
        }
    }

    /** With GPS on, the coordinate fields just show the fix; with it off they're editable. */
    private fun applyGpsMode() {
        val gps = Store.useGps(this)
        for (f in listOf(latField, lonField)) { f.isEnabled = !gps; f.alpha = if (gps) 0.5f else 1f }
        showStation()
    }

    private fun latLonText(lat: Double, lon: Double) = "%.4f° %s   %.4f° %s".format(java.util.Locale.US,
        kotlin.math.abs(lat), if (lat >= 0) "N" else "S", kotlin.math.abs(lon), if (lon >= 0) "E" else "W")

    private fun fixNote(l: Location) = buildString {
        if (l.hasAccuracy()) append("±%.0f m · ".format(l.accuracy))
        append((l.provider ?: "fix").uppercase())
        append(" · ").append(Checker.ukTime(l.time).uppercase())
    }

    /** Puts the saved station on the lit screen. */
    private fun showStation(animate: Boolean = false) {
        if (locating) return
        val st = Store.station(this)
        val gps = Store.useGps(this)
        val city = Store.placeCity(this)
        val foot = when {
            !gps -> "TYPED COORDINATES · GPS OFF"
            !Locator.hasPermission(this) -> "ALLOW LOCATION ACCESS TO USE GPS"
            else -> Store.fixNote(this) ?: "PHONE LOCATION · NO FIX YET"
        }
        screen.showResult(
            (city ?: if (gps && Store.fixNote(this) == null) "AWAITING FIX" else "YOUR STATION").uppercase(),
            (Store.placeCountry(this) ?: "—").uppercase(),
            latLonText(st.lat, st.lon),
            "LOCATOR ${Geo.latLonToLoc(st.lat, st.lon)}",
            foot, st.lat, st.lon, animate)
    }

    /** Makes a fix the station position (GPS mode only) and refreshes the fields. */
    private fun useFix(l: Location) {
        if (!Store.useGps(this)) return
        val old = Store.station(this)
        Store.saveStation(this, Station(l.latitude, l.longitude))
        Store.saveFixNote(this, fixNote(l))
        latField.setText("%.4f".format(java.util.Locale.US, l.latitude))
        lonField.setText("%.4f".format(java.util.Locale.US, l.longitude))
        // Moved more than ~5 km: the old place name no longer applies.
        if (Geo.km(old.lat, old.lon, l.latitude, l.longitude) > 5.0) Store.savePlace(this, null, null)
        showStation()
    }

    /** Looks up the town and country for the station in the background, then redraws the screen. */
    private fun nameStation(lat: Double, lon: Double, then: ((Locator.Place) -> Unit)? = null) {
        Thread {
            val p = Locator.place(this, lat, lon)
            runOnUiThread {
                val st = Store.station(this)
                // Only keep the name if the station hasn't moved on in the meantime.
                if ((p.city != null || p.country != null) && Geo.km(st.lat, st.lon, lat, lon) < 1.0) {
                    Store.savePlace(this, p.city, p.country)
                }
                if (then != null) then(p) else showStation()
            }
        }.start()
    }

    /** On app open: take the cached fix (no GPS warm-up) and name it in the background. */
    private fun refreshStationQuietly() {
        fun apply(l: Location) {
            useFix(l)
            if (Store.placeCity(this) == null) nameStation(l.latitude, l.longitude)
        }
        val cached = Locator.lastKnown(this)
        if (cached != null) apply(cached)
        // No cached fix, or an old one: get a fresh fix in the background.
        if (cached == null || System.currentTimeMillis() - cached.time > 30 * 60_000L) {
            Locator.current(this) { l -> if (l != null && !isFinishing && !locating) apply(l) }
        }
    }

    /** Tunes the Settings screen in: warm-up, sweep while the fix comes in, then the reading. */
    private fun checkMyLocation() {
        if (!Locator.hasPermission(this)) { requestPermissions(LOCATION_PERMS, REQ_LOCATE); return }
        if (locating) return
        if (!Locator.enabled(this)) {
            screen.showMessage("LOCATION IS SWITCHED OFF")
            locOnBtn.visibility = View.VISIBLE
            return
        }
        locOnBtn.visibility = View.GONE
        locating = true
        Sfx.startup(this)
        screen.showLocating()
        Locator.current(this) { l ->
            if (isFinishing) return@current
            if (l == null) {
                locating = false
                screen.showMessage("NO FIX — TRY NEAR A WINDOW")
                screen.postDelayed({ showStation() }, 4_000)
                return@current
            }
            val gps = Store.useGps(this)
            useFix(l)   // saves the fix (GPS mode); the screen keeps sweeping until the town is named
            nameStation(l.latitude, l.longitude) { p ->
                locating = false
                Sfx.detent(this)
                if (gps) showStation(animate = true)
                else screen.showResult(
                    (p.city ?: "Unknown town").uppercase(), (p.country ?: "Country unknown").uppercase(),
                    latLonText(l.latitude, l.longitude), "LOCATOR ${Geo.latLonToLoc(l.latitude, l.longitude)}",
                    fixNote(l) + " · NOT USED (GPS OFF)", l.latitude, l.longitude, true)
            }
        }
    }

    private fun checkForUpdate() {
        updateBtn.isEnabled = false
        updateBtn.text = "Checking…"
        Thread {
            val res = runCatching { Updater.check(this) }
            runOnUiThread {
                updateBtn.isEnabled = true
                updateBtn.text = "Check for updates"
                if (isFinishing) return@runOnUiThread
                res.onSuccess { info ->
                    if (info.newer) {
                        AlertDialog.Builder(this)
                            .setTitle("Update available")
                            .setMessage("Build ${info.latest} is available (you have build ${info.current}).\n\n" +
                                "Download it, then open PropWatch.apk to install over this version. " +
                                "Your settings are kept.")
                            .setPositiveButton("Download") { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.APK_URL)))
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    } else {
                        Toast.makeText(this, "You're up to date (build ${info.current})", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure {
                    Toast.makeText(this, "Couldn't check for updates: ${it.message ?: it.javaClass.simpleName}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun render(r: Report?, animateNeedle: Boolean) {
        results.removeAllViews()
        if (animateNeedle) dial.setLevel(level(r))
        if (r == null) {
            status.text = "NO READINGS YET — PRESS CHECK NOW"
            return
        }
        val list = shown(r)
        val alerts = list.count { it.alert && !it.lowGrade }
        val band = BANDS.firstOrNull { it.first == Store.band(this) }?.third ?: "ALL BANDS"
        status.text = "LAST CHECK ${Checker.ukTime(r.timeMs)} UK\n$band · " +
            if (alerts > 0) "$alerts ALERT${if (alerts > 1) "S" else ""}" else "CONDITIONS NORMAL"
        for (f in list) results.addView(card(f))
    }

    private fun card(f: Finding): View {
        val lamp = when {
            f.alert && !f.lowGrade -> Radio.LAMP_ALERT
            f.alert -> Radio.LAMP_HEADSUP
            f.headline == "Couldn't check" -> Radio.LAMP_ERROR
            else -> Radio.LAMP_OFF
        }
        val lit = lamp != Radio.LAMP_OFF
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = Radio.cardBg(this@MainActivity, if (lit) lamp else Color.argb(0x80, 0xF0, 0xE4, 0xC4))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(5), 0, dp(5)) }

            // Pilot lamp
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = dp(9).toFloat()
                    colors = intArrayOf(if (lit) Color.WHITE else lamp, lamp, Color.argb(0, 0, 0, 0))
                    setStroke(dp(1), Radio.CHROME_DARK)
                }
                alpha = if (lit) 1f else 0.6f
                layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply {
                    setMargins(0, dp(3), dp(12), 0)
                }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(context).apply {
                    text = f.mode.uppercase(); textSize = 11f; typeface = Radio.SERIF; letterSpacing = 0.2f
                    setTextColor(if (lit) lamp else Radio.GOLD)
                })
                addView(TextView(context).apply {
                    text = f.headline; textSize = 15f; setTextColor(Radio.CREAM); setPadding(0, dp(2), 0, dp(2))
                })
                if (f.detail.isNotBlank()) addView(TextView(context).apply {
                    text = f.detail; textSize = 13f; setTextColor(Radio.CREAM); alpha = 0.75f
                })
            })
        }
    }
}
