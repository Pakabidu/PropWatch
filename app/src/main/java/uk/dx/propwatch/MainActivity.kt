package uk.dx.propwatch

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
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
    private lateinit var updateBtn: Button
    private lateinit var latField: EditText
    private lateinit var lonField: EditText
    private lateinit var placeLabel: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Radio.LEATHER
        window.navigationBarColor = Radio.LEATHER
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

        // Front panel: grille, dial, status readout, main button.
        val front = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(14))
            background = Radio.panelBg(this@MainActivity, Radio.BRASS, 14f)
        }
        front.addView(GrilleView(this))
        dial = DialView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        front.addView(dial)
        status = TextView(this).apply {
            textSize = 12.5f; typeface = Radio.MONO; setTextColor(Radio.CREAM); alpha = 0.85f
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
        }
        front.addView(status)
        checkBtn = radioButton("Check now") { checkNow() }
        front.addView(checkBtn)
        root.addView(front)

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(4)) }
        root.addView(results)

        root.addView(section("Sources"))
        root.addView(link("Hepburn tropo forecast", "https://www.dxinfocentre.com/tropo.html"))
        root.addView(link("Live 144 MHz map (DXView)", "https://vhf.dxview.org/"))
        root.addView(link("NOAA SWPC 3-day forecast", "https://services.swpc.noaa.gov/text/3-day-forecast.txt"))
        root.addView(link("DXMaps (Es / tropo)", "https://www.dxmaps.com/spots/map.php?Lan=E&Frec=144"))
        root.addView(link("DXMaps 6m", "https://www.dxmaps.com/spots/map.php?Lan=E&Frec=50"))
        root.addView(link("HF band conditions (N0NBH)", "https://www.hamqsl.com/solar.html"))
        root.addView(link("PSKReporter map (all bands)", "https://pskreporter.info/pskmap.html"))

        root.addView(section("Settings"))
        val st = Store.station(this)
        val lat = number("Latitude", "%.4f".format(java.util.Locale.US, st.lat)).also { latField = it }
        val lon = number("Longitude", "%.4f".format(java.util.Locale.US, st.lon)).also { lonField = it }
        val interval = number("Check every (hours, 1–12)", Store.intervalHours(this).toString(), decimal = false)
        placeLabel = TextView(this).apply {
            textSize = 12f; typeface = Radio.MONO; setTextColor(Radio.CREAM); alpha = 0.8f; setPadding(0, dp(4), 0, dp(2))
        }
        root.addView(placeLabel)
        root.addView(radioSwitch("Use phone location (GPS)", Store.useGps(this)) { on ->
            Store.saveUseGps(this, on)   // applies immediately
            applyGpsMode()
            if (on) { if (Locator.hasPermission(this)) refreshStationQuietly() else requestPermissions(LOCATION_PERMS, REQ_REFRESH) }
        })
        root.addView(radioButton("Check my location") { checkMyLocation() })
        root.addView(label("Latitude")); root.addView(lat)
        root.addView(label("Longitude")); root.addView(lon)
        root.addView(label("Check every (hours, 1–12)")); root.addView(interval)
        val enabled = radioSwitch("Background monitoring", Store.enabled(this)) { }
        val sounds = radioSwitch("Radio switch sounds", Store.sounds(this)) { on ->
            Store.saveSounds(this, on)   // applies immediately, no need to Save
        }
        root.addView(enabled); root.addView(sounds)
        root.addView(radioButton("Save settings") {
            val la = lat.text.toString().toDoubleOrNull()
            val lo = lon.text.toString().toDoubleOrNull()
            val h = interval.text.toString().toIntOrNull()
            if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0 || h == null || h !in 1..12) {
                Toast.makeText(this, "Check the values", Toast.LENGTH_SHORT).show()
            } else {
                if (!Store.useGps(this)) {
                    Store.saveStation(this, Station(la, lo))
                    Store.savePlaceName(this, null)
                    updatePlaceLabel()
                }
                Store.saveInterval(this, h)
                Store.saveEnabled(this, enabled.isChecked)
                PropWorker.schedule(this)
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(radioButton("Send test notification") {
            Notifier.post(this, 99, Finding("Test", true, "PropWatch test",
                "Notifications are working. Real alerts look like this."))
        })
        updateBtn = radioButton("Check for updates") { checkForUpdate() }
        root.addView(updateBtn)
        root.addView(TextView(this).apply {
            textSize = 11f; typeface = Radio.MONO; setTextColor(Radio.BRASS); gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
            text = "PROPWATCH ${Updater.currentName(this@MainActivity)} · BUILD ${Updater.currentBuild(this@MainActivity)}"
        })
        root.addView(TextView(this).apply {
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

    private fun section(t: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(22), 0, dp(6))
        addView(TextView(context).apply {
            text = t.uppercase(); textSize = 13f; typeface = Radio.SERIF; letterSpacing = 0.3f
            setTextColor(Radio.BRASS)
        })
        addView(View(context).apply {
            setBackgroundColor(Radio.BRASS_DARK)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(5) }
        })
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

    private fun level(r: Report?): Int = when {
        r == null -> 0
        r.findings.any { it.alert && !it.lowGrade } -> 2
        r.findings.any { it.alert } -> 1
        else -> 0
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
        private const val REQ_STARTUP = 1
        private const val REQ_LOCATE = 2
        private const val REQ_REFRESH = 3
        private val LOCATION_PERMS = arrayOf(
            "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        val granted = Locator.hasPermission(this)
        updatePlaceLabel()
        if (granted) {
            when (code) {
                REQ_STARTUP, REQ_REFRESH -> if (Store.useGps(this)) refreshStationQuietly()
                REQ_LOCATE -> checkMyLocation()
            }
        } else if (code == REQ_LOCATE || code == REQ_REFRESH) {
            Toast.makeText(this, "Location permission refused — using the typed coordinates", Toast.LENGTH_LONG).show()
        }
    }

    /** With GPS on, the coordinate fields just show the fix; with it off they're editable. */
    private fun applyGpsMode() {
        val gps = Store.useGps(this)
        for (f in listOf(latField, lonField)) { f.isEnabled = !gps; f.alpha = if (gps) 0.5f else 1f }
        updatePlaceLabel()
    }

    private fun updatePlaceLabel() {
        val st = Store.station(this)
        val loc = Geo.latLonToLoc(st.lat, st.lon)
        val src = when {
            !Store.useGps(this) -> "typed coordinates"
            !Locator.hasPermission(this) -> "GPS — permission needed"
            else -> "phone location"
        }
        placeLabel.text = "STATION: ${Store.placeName(this) ?: "%.3f, %.3f".format(st.lat, st.lon)} · $loc\n($src)"
    }

    /** Makes a fix the station position (GPS mode only) and refreshes the fields. */
    private fun useFix(l: Location) {
        if (!Store.useGps(this)) return
        val old = Store.station(this)
        Store.saveStation(this, Station(l.latitude, l.longitude))
        latField.setText("%.4f".format(java.util.Locale.US, l.latitude))
        lonField.setText("%.4f".format(java.util.Locale.US, l.longitude))
        // Moved more than ~5 km: the old place name no longer applies.
        if (Geo.km(old.lat, old.lon, l.latitude, l.longitude) > 5.0) Store.savePlaceName(this, null)
        updatePlaceLabel()
    }

    private fun placeText(p: Locator.Place) = listOfNotNull(p.city, p.country).joinToString(", ").ifEmpty { null }

    /** On app open: take the cached fix (no GPS warm-up) and name it in the background. */
    private fun refreshStationQuietly() {
        fun apply(l: Location) {
            useFix(l)
            if (Store.placeName(this) != null) return
            Thread {
                val name = placeText(Locator.place(this, l.latitude, l.longitude))
                runOnUiThread { if (name != null && Store.useGps(this)) { Store.savePlaceName(this, name); updatePlaceLabel() } }
            }.start()
        }
        val cached = Locator.lastKnown(this)
        if (cached != null) apply(cached)
        // No cached fix, or an old one: get a fresh fix in the background.
        if (cached == null || System.currentTimeMillis() - cached.time > 30 * 60_000L) {
            Locator.current(this) { l -> if (l != null && !isFinishing) apply(l) }
        }
    }

    private fun checkMyLocation() {
        if (!Locator.hasPermission(this)) { requestPermissions(LOCATION_PERMS, REQ_LOCATE); return }

        val screen = LocationScreenView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(10))
            background = Radio.panelBg(this@MainActivity, Radio.BRASS, 16f)
            addView(screen)
        }
        val dialog = Dialog(this)
        val settingsBtn = radioButton("Location settings") {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); dialog.dismiss()
        }.apply { visibility = View.GONE }
        box.addView(settingsBtn)
        box.addView(radioButton("Close") { dialog.dismiss() })
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT)
        dialog.show()

        if (!Locator.enabled(this)) {
            screen.showMessage("LOCATION IS SWITCHED OFF")
            settingsBtn.visibility = View.VISIBLE
            return
        }
        Sfx.startup(this)
        screen.showLocating()
        Locator.current(this) { l ->
            if (!dialog.isShowing) return@current
            if (l == null) { screen.showMessage("NO FIX — TRY NEAR A WINDOW"); return@current }
            useFix(l)
            Thread {
                val p = Locator.place(this, l.latitude, l.longitude)
                runOnUiThread {
                    val name = placeText(p)
                    if (name != null && Store.useGps(this)) { Store.savePlaceName(this, name); updatePlaceLabel() }
                    if (!dialog.isShowing) return@runOnUiThread
                    Sfx.detent(this)
                    val ns = if (l.latitude >= 0) "N" else "S"
                    val ew = if (l.longitude >= 0) "E" else "W"
                    val latLon = "%.4f° %s   %.4f° %s".format(java.util.Locale.US,
                        kotlin.math.abs(l.latitude), ns, kotlin.math.abs(l.longitude), ew)
                    val age = (System.currentTimeMillis() - l.time) / 60_000
                    val foot = buildString {
                        if (l.hasAccuracy()) append("±%.0f m · ".format(l.accuracy))
                        append((l.provider ?: "fix").uppercase())
                        if (age >= 2) append(" · $age MIN OLD")
                        if (!Store.useGps(this@MainActivity)) append(" · NOT USED (GPS OFF)")
                    }
                    screen.showResult(
                        (p.city ?: "Unknown town").uppercase(),
                        (p.country ?: "Country unknown").uppercase(),
                        latLon,
                        "LOCATOR ${Geo.latLonToLoc(l.latitude, l.longitude)}",
                        foot)
                }
            }.start()
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
        val alerts = r.findings.count { it.alert && !it.lowGrade }
        status.text = "LAST CHECK ${Checker.ukTime(r.timeMs)} UK · " +
            if (alerts > 0) "$alerts ALERT${if (alerts > 1) "S" else ""}" else "CONDITIONS NORMAL"
        for (f in r.findings) results.addView(card(f))
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
            background = Radio.panelBg(this@MainActivity, if (lit) lamp else Radio.BRASS_DARK)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(5), 0, dp(5)) }

            // Pilot lamp
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = dp(9).toFloat()
                    colors = intArrayOf(if (lit) Color.WHITE else lamp, lamp, Color.argb(0, 0, 0, 0))
                    setStroke(dp(1), Radio.BRASS_DARK)
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
                    setTextColor(if (lit) lamp else Radio.BRASS)
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
