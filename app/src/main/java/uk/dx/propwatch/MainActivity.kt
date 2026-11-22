package uk.dx.propwatch

import android.app.Activity
import android.app.AlertDialog
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Radio.LEATHER
        window.navigationBarColor = Radio.LEATHER
        Notifier.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
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
        val lat = number("Latitude", st.lat.toString())
        val lon = number("Longitude", st.lon.toString())
        val interval = number("Check every (hours, 1–12)", Store.intervalHours(this).toString(), decimal = false)
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
                Store.saveStation(this, Station(la, lo))
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
