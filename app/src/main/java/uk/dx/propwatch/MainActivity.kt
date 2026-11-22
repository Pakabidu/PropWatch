package uk.dx.propwatch

import android.app.Activity
import android.app.AlertDialog
import android.location.Location
import android.provider.Settings
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/** Single screen, built in code so there's no layout XML to maintain. Styled as a 1940s portable receiver. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var checkBtn: Button
    // Front panel parts: the portable cabinet uses the first three, the receiver the last.
    private var dial: DialView? = null
    private var bandPlate: BandPlateView? = null
    private var tonePlate: TonePlateView? = null
    private var receiver: FrontPanel? = null          // receiver or aircraft drawn panel
    private var aircraft: AircraftPanelView? = null
    private lateinit var tropoBox: LinearLayout
    private var microwaveOpen = false
    private lateinit var logChart: LogChartView
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
        Radio.skin = Store.skin(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        when (Radio.skin) {
            Radio.Skin.RECEIVER -> buildReceiverFront(root)
            Radio.Skin.AIRCRAFT -> buildAircraftFront(root)
            else -> buildPortableFront(root)
        }

        // The open lid: log chart, readings, sources and settings on a black instruction panel.
        val lid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(24), dp(26), dp(26))
            background = Radio.LidDrawable(this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
        }
        root.addView(lid)
        // Station log first: the map with the station pinned, and Check my location.
        screen = StationLogView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, dp(6)) }
        }
        lid.addView(screen)
        locOnBtn = radioButton("Turn on location") {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }.apply { visibility = View.GONE }
        lid.addView(locOnBtn)
        lid.addView(radioButton("Check my location", silent = true) { checkMyLocation() })

        lid.addView(section("PropWatch · DX Log", pill = true))
        logChart = LogChartView(this)
        lid.addView(logChart)

        lid.addView(section("Readings"))
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(2), 0, dp(4)) }
        lid.addView(results)

        lid.addView(section("Sources"))
        lid.addView(sourcesTable(listOf(
            Triple("Hepburn tropo forecast", "2M · 70", "https://www.dxinfocentre.com/tropo.html"),
            Triple("Live 144 MHz map (DXView)", "2M", "https://vhf.dxview.org/"),
            Triple("NOAA SWPC 3-day forecast", "ALL", "https://services.swpc.noaa.gov/text/3-day-forecast.txt"),
            Triple("DXMaps · Es and tropo", "2M", "https://www.dxmaps.com/spots/map.php?Lan=E&Frec=144"),
            Triple("DXMaps · 6 metres", "6M", "https://www.dxmaps.com/spots/map.php?Lan=E&Frec=50"),
            Triple("HF band conditions (N0NBH)", "HF", "https://www.hamqsl.com/solar.html"),
            Triple("PSKReporter map", "ALL", "https://pskreporter.info/pskmap.html"),
        )))

        lid.addView(section("Tropo bands"))
        tropoBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lid.addView(tropoBox)
        refreshTropoBands()

        lid.addView(section("Settings"))
        lid.addView(label("Cabinet"))
        for ((skin, name) in listOf(Radio.Skin.PORTABLE to "1940s portable", Radio.Skin.RECEIVER to "Communications receiver",
                Radio.Skin.AIRCRAFT to "WW2 aircraft wireless")) {
            lid.addView(radioButton((if (Radio.skin == skin) "● " else "○ ") + name) {
                if (Radio.skin != skin) {
                    Store.saveSkin(this, skin)
                    recreate()   // rebuild the whole screen in the new cabinet
                }
            })
        }
        val st = Store.station(this)
        val lat = number("Latitude", "%.4f".format(java.util.Locale.US, st.lat)).also { latField = it }
        val lon = number("Longitude", "%.4f".format(java.util.Locale.US, st.lon)).also { lonField = it }
        val interval = number("Check every (hours, 1–12)", Store.intervalHours(this).toString(), decimal = false)
        lid.addView(label("Latitude")); lid.addView(lat)
        lid.addView(label("Longitude")); lid.addView(lon)
        lid.addView(label("Check every (hours, 1–12)")); lid.addView(interval)
        lid.addView(TextView(this).apply {
            textSize = 14f; typeface = Radio.MONO; setTextColor(Radio.CREAM); alpha = 0.9f; setPadding(0, dp(10), 0, dp(2))
            text = "Front-panel rockers: SOUND switch sounds · MONITOR background checks · GPS phone location. " +
                "They take effect at once."
        })
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
            textSize = 13f; typeface = Radio.MONO; setTextColor(Radio.BRASS); gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
            text = "PROPWATCH ${Updater.currentName(this@MainActivity)} · BUILD ${Updater.currentBuild(this@MainActivity)}"
        })
        lid.addView(section("Support PropWatch"))
        lid.addView(donatePanel())
        lid.addView(TextView(this).apply {
            textSize = 15f; setTextColor(Radio.CREAM); alpha = 0.8f; setPadding(0, dp(16), 0, 0)
            text = "Alerts only fire when a threshold is met: tropo ducting forecast along the North Sea paths " +
                "or a live cluster of ≥700 km spots, Kp ≥ 6- (or a G2 watch), 2m or 6m Es in progress, 6m " +
                "long-haul DX, or a major meteor shower peak or 10m/12m opening (heads-ups). HF conditions for " +
                "80–10m are shown for reference. The tropo forecast is a model estimate, not Hepburn's index."
        })

        setContentView(ScrollView(this).apply {
            background = Radio.background(this@MainActivity)
            isFillViewport = true
            addView(root)
        })

        applyGpsMode()
        if (Store.useGps(this) && Locator.hasPermission(this)) refreshStationQuietly()
        if (Store.placeCity(this) == null && !Store.useGps(this)) Store.station(this).let { nameStation(it.lat, it.lon) }

        val report = Store.loadReport(this)
        render(report, animateNeedle = false)
        if (savedInstanceState == null) {
            // The dial lamps warm up and a fresh check starts every time the app opens,
            // with the same valve warm-up sound as pressing Check now.
            dial?.warmUp(level(report))
            receiver?.warmUp(Bands.level(report?.findings.orEmpty(), Store.band(this)), kp(report))
            checkNow()
        }
    }

    /** The 1940s portable: handle, grille and tone plate, dial, waveband plate, readout, main button. */
    private fun buildPortableFront(root: LinearLayout) {
        // Carrying handle, then the case front: grille and tone plate, dial, band plate, readout, main button.
        root.addView(HandleView(this))
        val front = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(0x40, 0, 0, 0)); cornerRadius = dp(16).toFloat(); setStroke(dp(3), Color.rgb(0x05, 0x04, 0x04))
            }
        }
        val upper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200))
        }
        upper.addView(GrilleView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.6f)
        })
        val tonePlateView = TonePlateView(this).apply {
            switches = toneSwitches()
            onToggle = { i, on -> toggle(i, on) }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = dp(10) }
        }
        tonePlate = tonePlateView
        upper.addView(tonePlateView)
        front.addView(upper)
        val dialView = DialView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        dial = dialView
        front.addView(dialView)
        val bandPlateView = BandPlateView(this).apply {
            selected = BAND_KEYS.indexOf(Store.band(this@MainActivity)).coerceAtLeast(0)
            onSelect = { i ->
                Sfx.click(this@MainActivity)
                Store.saveBand(this@MainActivity, BAND_KEYS[i])
                render(Store.loadReport(this@MainActivity), animateNeedle = true)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
        }
        bandPlate = bandPlateView
        front.addView(bandPlateView)
        status = TextView(this).apply {
            textSize = 15f; typeface = Radio.MONO; setTextColor(Radio.CREAM); alpha = 0.95f
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
        }
        front.addView(status)
        checkBtn = radioButton("Check now", silent = true) { checkNow() }
        front.addView(checkBtn)
        root.addView(front)
    }

    /**
     * The communications receiver: speaker cabinet on top, then the receiver's crackle
     * front panel (dials, meters, nameplate, working knobs and toggles), readout and button.
     */
    private fun buildReceiverFront(root: LinearLayout) {
        root.addView(SpeakerCabinetView(this))
        root.addView(View(this).apply {   // the cabinet's top lip
            background = Radio.tile(this@MainActivity, Radio.greyCrackle)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12))
        })
        val front = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Radio.tile(this@MainActivity, Radio.blackCrackle)
            setPadding(0, 0, 0, dp(14))
        }
        val panel = ReceiverPanelView(this).apply {
            bands = BAND_KEYS.map { k -> if (k == "all") "ALL" else Bands.ALL.first { it.first == k }.second }
            selected = BAND_KEYS.indexOf(Store.band(this@MainActivity)).coerceAtLeast(0)
            toggles = toneSwitches()
            onCheck = { checkNow() }
            onSelect = { i ->
                Sfx.detent(this@MainActivity)
                Store.saveBand(this@MainActivity, BAND_KEYS[i])
                render(Store.loadReport(this@MainActivity), animateNeedle = true)
            }
            onToggle = { i, on -> toggle(i, on) }
        }
        receiver = panel
        front.addView(panel)
        status = TextView(this).apply {
            textSize = 15f; typeface = Radio.MONO; setTextColor(Radio.CREAM)
            gravity = Gravity.CENTER; setPadding(dp(12), dp(4), dp(12), dp(10))
        }
        front.addView(status)
        checkBtn = radioButton("Check now", silent = true) { checkNow() }.apply {
            (layoutParams as LinearLayout.LayoutParams).setMargins(dp(14), dp(4), dp(14), dp(4))
        }
        front.addView(checkBtn)
        root.addView(front)
    }

    /**
     * The WW2 aircraft wireless set: one drawn panel (meters, semicircular dial, big
     * knurled knob, station record, coloured levers, band switch), then readout and button.
     */
    private fun buildAircraftFront(root: LinearLayout) {
        val front = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Radio.tile(this@MainActivity, Radio.blackEnamel)
            setPadding(0, 0, 0, dp(14))
        }
        val panel = AircraftPanelView(this).apply {
            bands = BAND_KEYS.map { k -> if (k == "all") "ALL" else Bands.ALL.first { it.first == k }.second }
            selected = BAND_KEYS.indexOf(Store.band(this@MainActivity)).coerceAtLeast(0)
            toggles = toneSwitches()
            record = stationRecord()
            onCheck = { checkNow() }
            onSelect = { i ->
                Sfx.detent(this@MainActivity)
                Store.saveBand(this@MainActivity, BAND_KEYS[i])
                render(Store.loadReport(this@MainActivity), animateNeedle = true)
            }
            onToggle = { i, on -> toggle(i, on) }
        }
        receiver = panel
        aircraft = panel
        front.addView(panel)
        status = TextView(this).apply {
            textSize = 15f; typeface = Radio.MONO; setTextColor(Radio.CREAM)
            gravity = Gravity.CENTER; setPadding(dp(12), dp(4), dp(12), dp(10))
        }
        front.addView(status)
        checkBtn = radioButton("Check now", silent = true) { checkNow() }.apply {
            (layoutParams as LinearLayout.LayoutParams).setMargins(dp(14), dp(4), dp(14), dp(4))
        }
        front.addView(checkBtn)
        root.addView(front)
    }

    /** Lines for the aircraft set's station-record card. */
    private fun stationRecord(): List<String> {
        val st = Store.station(this)
        return listOf("LOCATOR ${Geo.latLonToLoc(st.lat, st.lon).uppercase()}",
            "BUILD ${Updater.currentBuild(this)}", "TYPE PW-44")
    }

    // ---- Tropo band settings ----

    /**
     * Rebuilds the Tropo bands section: the station's ITU region, a toggle for each core
     * band, the microwave group (collapsed until opened) and a reset to the regional
     * defaults. Only choices that differ from the default are stored.
     */
    private fun refreshTropoBands() {
        tropoBox.removeAllViews()
        val grid = Store.stationGrid(this)
        val region = TropoBands.ituRegionForGrid(grid)
        val defaults = TropoBands.getDefaultBandsForGrid(grid).map { it.id }.toSet()
        val overrides = Store.tropoBandOverrides(this)
        val visible = TropoBands.resolveVisibleBands(grid, overrides).map { it.id }.toSet()
        tropoBox.addView(TextView(this).apply {
            textSize = 14f; typeface = Radio.MONO; setTextColor(Radio.CREAM); alpha = 0.9f; setPadding(0, dp(2), 0, dp(6))
            text = "ITU REGION ${region?.number ?: "?"} · LOCATOR ${grid.uppercase()}\n" +
                "Tap to choose the bands you work. Defaults follow your region."
        })
        for (b in TropoBands.CORE) tropoBox.addView(bandToggle(b, region, b.id in visible, b.id in defaults))
        val shownMw = TropoBands.MICROWAVE.count { it.id in visible }
        tropoBox.addView(radioButton((if (microwaveOpen) "▾ " else "▸ ") + "Microwave bands" +
                if (shownMw > 0) " ($shownMw shown)" else "") {
            microwaveOpen = !microwaveOpen
            refreshTropoBands()
        })
        if (microwaveOpen) for (b in TropoBands.MICROWAVE) tropoBox.addView(bandToggle(b, region, b.id in visible, b.id in defaults))
        if (overrides.isNotEmpty()) tropoBox.addView(radioButton("Reset to Region ${region?.number ?: ""} defaults") {
            Store.clearTropoBandOverrides(this)
            refreshTropoBands()
        })
    }

    /** One band: lamp, label and frequency, with its allocation underneath. Tapping flips it. */
    private fun bandToggle(b: TropoBand, region: ItuRegion?, on: Boolean, isDefault: Boolean): View {
        val freq = if (b.frequencyMHz >= 1000) "%.1f GHz".format(java.util.Locale.US, b.frequencyMHz / 1000)
            else "%.0f MHz".format(java.util.Locale.US, b.frequencyMHz)
        val note = when {
            region != null && region !in b.ituRegions -> "Not allocated in Region ${region.number}. " + b.allocationNote
            b.nationalOnly -> "National allocations only. " + b.allocationNote
            else -> b.allocationNote
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = Radio.buttonBg(this@MainActivity, big = false)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(4)) }
            addView(TextView(context).apply {
                text = (if (on) "●  " else "○  ") + "${b.label}  ·  $freq" + if (on == isDefault) "" else "   (changed)"
                textSize = 17f; typeface = Radio.SERIF
                setTextColor(if (on) Radio.LAMP_ALERT else Radio.CREAM)
            })
            addView(TextView(context).apply {
                text = note; textSize = 13f; setTextColor(Radio.CREAM); alpha = 0.75f; setPadding(0, dp(2), 0, 0)
            })
            isClickable = true
            setOnClickListener {
                Sfx.click(this@MainActivity)
                val now = !on
                // Store only a real change from the default; flipping back forgets the choice.
                Store.setTropoBandOverride(this@MainActivity, b.id, if (now == isDefault) null else now)
                refreshTropoBands()
            }
        }
    }

    // ---- Styled widgets ----

    /** Bakelite push-button. [silent] skips the switch click, for buttons with their own sound. */
    private fun radioButton(t: String, silent: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = t
        typeface = Radio.SERIF
        letterSpacing = 0.12f
        setTextColor(Radio.CREAM)
        background = Radio.buttonBg(this@MainActivity)
        stateListAnimator = null
        textSize = 17f
        minHeight = dp(60)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(6), 0, dp(6)) }
        setOnClickListener { if (!silent) Sfx.click(this@MainActivity); onClick() }
    }

    /**
     * Ruled cream-on-black table, like the station list printed in the lid.
     * Rows: name, bands, URL. Tapping a row opens it.
     */
    private fun sourcesTable(rows: List<Triple<String, String, String>>): View {
        val rule = Color.argb(0xB0, 0xF0, 0xE4, 0xC4)
        fun line() = View(this).apply {
            setBackgroundColor(rule)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        }
        fun row(a: String, b: String, header: Boolean) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(if (header) 4 else 11), dp(10), dp(if (header) 4 else 11))
            if (header) setBackgroundColor(Radio.CREAM)
            addView(TextView(context).apply {
                text = a; textSize = if (header) 13f else 17f
                typeface = if (header) Radio.SERIF else Typeface.SERIF
                letterSpacing = if (header) 0.3f else 0f
                setTextColor(if (header) Radio.INK else Radio.CREAM)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = b; textSize = 13f; typeface = Radio.SERIF; letterSpacing = 0.1f; gravity = Gravity.END
                setTextColor(if (header) Radio.INK else Radio.GOLD)
                layoutParams = LinearLayout.LayoutParams(dp(78), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT); setStroke(dp(1), rule) }
            addView(row("SOURCE", "BANDS", header = true))
            for ((name, bands, url) in rows) {
                addView(line())
                addView(row("▸  $name", bands, header = false).apply {
                    background = android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_pressed), android.graphics.drawable.ColorDrawable(Color.argb(0x40, 0xF0, 0xE4, 0xC4)))
                    }
                    isClickable = true
                    setOnClickListener {
                        Sfx.detent(this@MainActivity)
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                })
            }
        }
    }

    /**
     * The coffee fund: a cheeky one-pound ask, then a slider for £1–£10 and a
     * PayPal button. Entirely optional; nothing in the app is locked behind it.
     */
    private fun donatePanel(): View {
        var pounds = Donate.MIN
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply {
            textSize = 17f; typeface = Typeface.SERIF; setTextColor(Radio.CREAM); setPadding(0, dp(2), 0, dp(4))
            text = "PropWatch is free and always will be. It runs on valves, solder and strong coffee, though. " +
                "If it's caught you an opening, would you stand the operator a £1 cuppa?"
        })
        val chooser = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(Color.rgb(0x0A, 0x09, 0x08)); setStroke(dp(1), Radio.GOLD) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        val amount = TextView(this).apply {
            textSize = 44f; typeface = Radio.SERIF; setTextColor(Radio.GOLD); gravity = Gravity.CENTER
        }
        val cups = TextView(this).apply {
            textSize = 13f; typeface = Radio.SERIF; letterSpacing = 0.25f; setTextColor(Radio.CREAM); gravity = Gravity.CENTER
        }
        val quip = TextView(this).apply {
            textSize = 17f; typeface = Radio.SERIF_ITALIC; setTextColor(Radio.CREAM); alpha = 0.95f
            gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(4)); minLines = 2
        }
        lateinit var pay: Button
        fun show() {
            amount.text = "£$pounds"
            cups.text = Donate.cups(pounds)
            quip.text = Donate.quip(pounds)
            pay.text = "Donate £$pounds with PayPal"
        }
        val slider = SeekBar(this).apply {
            max = Donate.MAX - Donate.MIN
            progress = 0
            progressTintList = ColorStateList.valueOf(Radio.GOLD)
            thumbTintList = ColorStateList.valueOf(Radio.CREAM)
            progressBackgroundTintList = ColorStateList.valueOf(Radio.CHROME_DARK)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (Donate.MIN + p != pounds) { pounds = Donate.MIN + p; show(); if (fromUser) Sfx.detent(this@MainActivity) }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        val ends = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
            addView(TextView(context).apply {
                text = "£${Donate.MIN}"; textSize = 14f; typeface = Radio.SERIF; setTextColor(Radio.CREAM)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(context).apply {
                text = "£${Donate.MAX}"; textSize = 14f; typeface = Radio.SERIF; setTextColor(Radio.CREAM)
            })
        }
        pay = radioButton("") {
            startActivity(Intent(Intent.ACTION_VIEW, Donate.url(pounds)))
        }
        chooser.addView(amount); chooser.addView(cups); chooser.addView(quip)
        chooser.addView(slider); chooser.addView(ends); chooser.addView(pay)
        chooser.addView(TextView(this).apply {
            textSize = 14f; setTextColor(Radio.CREAM); alpha = 0.85f; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0)
            text = "Opens PayPal in your browser. Pay by card or PayPal account; nothing is charged in the app."
        })
        show()

        val ask = radioButton("Buy me a coffee — £1") {}
        ask.setOnClickListener {
            Sfx.click(this)
            val open = chooser.visibility != View.VISIBLE
            chooser.visibility = if (open) View.VISIBLE else View.GONE
            ask.text = if (open) "Maybe later" else "Buy me a coffee — £1"
        }
        box.addView(ask)
        box.addView(chooser)
        return box
    }

    /**
     * Section heading. Normal: a cream band with widely spaced black capitals, like the
     * lid's "operating instructions" headers. pill: the rounded nameplate across the top.
     */
    private fun section(t: String, first: Boolean = false, pill: Boolean = false) = TextView(this).apply {
        textSize = if (pill) 14f else 13.5f
        letterSpacing = if (pill) 0.3f else 0.22f
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(if (pill) 5 else 3), dp(8), dp(if (pill) 5 else 3))
        if (Radio.aircraft) {
            // Aircraft set: black capitals engraved on a brass label plate.
            text = t.uppercase()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Color.rgb(0x15, 0x12, 0x0C))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Radio.PLATE_LIGHT, Radio.PLATE, Radio.PLATE_DARK)).apply { cornerRadius = dp(2).toFloat() }
        } else if (Radio.receiver) {
            // Receiver: white engraved capitals between red chevrons, like its nameplate.
            val label = "›››  ${t.uppercase()}  ‹‹‹"
            text = android.text.SpannableString(label).apply {
                val red = Radio.PANEL_RED
                setSpan(android.text.style.ForegroundColorSpan(red), 0, 3, 0)
                setSpan(android.text.style.ForegroundColorSpan(red), label.length - 3, label.length, 0)
            }
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setTextColor(Radio.CREAM)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT); cornerRadius = dp(4).toFloat(); setStroke(dp(1), Color.argb(0x80, 0xEC, 0xE8, 0xDC))
            }
        } else {
            text = t.uppercase(); typeface = Radio.SERIF
            setTextColor(Radio.INK)
            background = GradientDrawable().apply {
                setColor(Radio.CREAM); cornerRadius = dp(if (pill) 14 else 1).toFloat()
            }
        }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, if (first) 0 else dp(20), 0, dp(8)) }
    }

    private fun label(t: String) = TextView(this).apply {
        text = t; textSize = 14f; typeface = Radio.MONO; setTextColor(Radio.BRASS); setPadding(0, dp(10), 0, 0)
    }

    private fun number(hint: String, value: String, decimal: Boolean = true) = EditText(this).apply {
        this.hint = hint
        setText(value)
        typeface = Radio.MONO
        textSize = 18f
        setTextColor(Radio.CREAM)
        setHintTextColor(Color.argb(0x80, 0xF1, 0xE6, 0xC8))
        backgroundTintList = ColorStateList.valueOf(Radio.BRASS)
        inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_NUMBER
    }

    // ---- Behaviour ----

    /** Needle position for the selected waveband: 0 quiet, 1 heads-up, 2 open. */
    private fun level(r: Report?): Int = Bands.level(r?.findings.orEmpty(), Store.band(this)).coerceAtLeast(0)

    /** Latest planetary K index from the aurora reading ("Kp 3.67 …"), for the receiver's Kp meter. */
    private fun kp(r: Report?): Float? = r?.findings?.firstOrNull { it.mode == "Aurora" }?.let { f ->
        Regex("Kp (\\d+(?:\\.\\d+)?)").find(f.headline + " " + f.detail)?.groupValues?.get(1)?.toFloatOrNull()
    }

    /** The readings for the selected waveband button. */
    private fun shown(r: Report?): List<Finding> {
        val band = Store.band(this)
        return r?.findings.orEmpty().filter { band == "all" || band in Bands.of(it.mode) }
    }

    private fun bandName(key: String) = if (key == "all") "ALL BANDS" else Bands.ALL.first { it.first == key }.third

    /** Waveband plate rows: each band's live condition beside its button. */
    private fun bandRows(r: Report?) = BAND_KEYS.map { key ->
        val lv = if (r == null) null else Bands.level(r.findings, key)
        BandPlateView.Row(bandName(key), when (lv) {
            null -> "—"; 2 -> "OPEN"; 1 -> "HEADS-UP"; -1 -> "NO DATA"; else -> "QUIET"
        }, when (lv) { 2 -> Radio.LAMP_ALERT; 1 -> Radio.LAMP_HEADSUP; -1 -> Radio.LAMP_ERROR; else -> null })
    }

    private fun toneSwitches() = listOf(
        "SOUND" to Store.sounds(this), "MONITOR" to Store.enabled(this), "GPS" to Store.useGps(this))

    /** A tone-plate rocker was flipped. */
    private fun toggle(i: Int, on: Boolean) {
        when (i) {
            0 -> Store.saveSounds(this, on)
            1 -> {
                Store.saveEnabled(this, on)
                PropWorker.schedule(this)
                Toast.makeText(this, if (on) "Background monitoring on" else "Background monitoring off", Toast.LENGTH_SHORT).show()
            }
            2 -> {
                Store.saveUseGps(this, on)
                applyGpsMode()
                if (on) { if (Locator.hasPermission(this)) refreshStationQuietly() else requestPermissions(LOCATION_PERMS, REQ_REFRESH) }
            }
        }
        Sfx.click(this)
    }

    /**
     * Runs every check in the background, with the valve warm-up sound. Used by the
     * Check now button, the receiver's vernier knob and the automatic check at startup.
     */
    private fun checkNow() {
        if (!checkBtn.isEnabled) return   // a check is already running
        Sfx.tuneIn(this)
        checkBtn.isEnabled = false
        checkBtn.text = "Tuning…"
        receiver?.setChecking(true)
        if (Store.useGps(this)) Locator.lastKnown(this)?.let { useFix(it) }
        val station = Store.station(this)
        Thread {
            val r = Checker(station).run()
            Store.saveReport(this, r)
            Notifier.handle(this, r)
            runOnUiThread {
                checkBtn.isEnabled = true
                checkBtn.text = "Check now"
                receiver?.setChecking(false)
                render(r, animateNeedle = true)
            }
        }.start()
    }

    // ---- Location ----

    companion object {
        /** Waveband buttons, top to bottom: "all" then each band. */
        private val BAND_KEYS = listOf("all") + Bands.ALL.map { it.first }
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

    /** Puts the saved station on the lit screen (and the aircraft set's record card). */
    private fun showStation(animate: Boolean = false) {
        aircraft?.record = stationRecord()
        if (::tropoBox.isInitialized) refreshTropoBands()   // the ITU region follows the station
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
        Sfx.locate(this)   // D/F sweep, then "QTH?" in Morse
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
        bandPlate?.rows = bandRows(r)
        receiver?.bandLamps = bandRows(r).map { it.lamp }
        logChart.entries = Store.history(this)
        if (animateNeedle) dial?.setLevel(level(r))
        receiver?.show(Bands.level(r?.findings.orEmpty(), Store.band(this)), kp(r), animateNeedle)
        if (r == null) {
            status.text = "NO READINGS YET — PRESS CHECK NOW"
            return
        }
        val list = shown(r)
        val alerts = list.count { it.alert && !it.lowGrade }
        val band = bandName(Store.band(this))
        status.text = "LAST CHECK ${Checker.ukTime(r.timeMs)} UK\n$band · " +
            if (alerts > 0) "$alerts ALERT${if (alerts > 1) "S" else ""}" else "CONDITIONS NORMAL"
        for (f in list) results.addView(card(f))
    }

    /** A reading, boxed like a paragraph of the lid's operating instructions. */
    private fun card(f: Finding): View {
        val lamp = when {
            f.alert && !f.lowGrade -> Radio.LAMP_ALERT
            f.alert -> Radio.LAMP_HEADSUP
            f.headline == "Couldn't check" -> Radio.LAMP_ERROR
            else -> Radio.LAMP_OFF
        }
        val lit = lamp != Radio.LAMP_OFF
        val rule = if (lit) lamp else Color.argb(0xB0, 0xF0, 0xE4, 0xC4)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.rgb(0x0A, 0x09, 0x08)); setStroke(dp(1), rule) }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(5), 0, dp(5)) }
            // Header band with the mode name.
            addView(TextView(context).apply {
                text = f.mode.uppercase(); textSize = 12.5f; typeface = Radio.SERIF; letterSpacing = 0.22f
                gravity = Gravity.CENTER; setTextColor(Radio.INK); setPadding(0, dp(2), 0, dp(2))
                setBackgroundColor(if (lit) lamp else Radio.CREAM)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
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
                    layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { setMargins(0, dp(3), dp(12), 0) }
                })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(context).apply {
                        text = f.headline; textSize = 19f; setTextColor(Radio.CREAM); setPadding(0, 0, 0, dp(4))
                    })
                    if (f.detail.isNotBlank()) addView(TextView(context).apply {
                        text = f.detail; textSize = 16f; setTextColor(Radio.CREAM); alpha = 0.88f
                    })
                })
            })
        }
    }
}
