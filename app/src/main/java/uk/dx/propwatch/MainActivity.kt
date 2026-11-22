package uk.dx.propwatch

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Single screen, built in code so there's no layout XML to maintain. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var checkBtn: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
        PropWorker.schedule(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        root.addView(TextView(this).apply {
            text = "2m / 70cm DX watch"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })
        status = TextView(this).apply { textSize = 13f; alpha = 0.75f; setPadding(0, dp(4), 0, dp(12)) }
        root.addView(status)

        checkBtn = Button(this).apply { text = "Check now"; setOnClickListener { checkNow() } }
        root.addView(checkBtn)

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
        root.addView(results)

        root.addView(section("Sources"))
        root.addView(link("Hepburn tropo forecast", "https://www.dxinfocentre.com/tropo.html"))
        root.addView(link("Live 144 MHz map (DXView)", "https://vhf.dxview.org/"))
        root.addView(link("NOAA SWPC 3-day forecast", "https://services.swpc.noaa.gov/text/3-day-forecast.txt"))
        root.addView(link("DXMaps (Es / tropo)", "https://www.dxmaps.com/spots/map.php?Lan=E&Frec=144"))

        root.addView(section("Settings"))
        val st = Store.station(this)
        val lat = number("Latitude", st.lat.toString())
        val lon = number("Longitude", st.lon.toString())
        val interval = number("Check every (hours, 1–12)", Store.intervalHours(this).toString(), decimal = false)
        val enabled = Switch(this).apply {
            text = "Background monitoring"
            isChecked = Store.enabled(this@MainActivity)
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(lat); root.addView(lon); root.addView(interval); root.addView(enabled)
        root.addView(Button(this).apply {
            text = "Save settings"
            setOnClickListener {
                val la = lat.text.toString().toDoubleOrNull()
                val lo = lon.text.toString().toDoubleOrNull()
                val h = interval.text.toString().toIntOrNull()
                if (la == null || lo == null || la !in -90.0..90.0 || lo !in -180.0..180.0 || h == null || h !in 1..12) {
                    Toast.makeText(this@MainActivity, "Check the values", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                Store.saveStation(this@MainActivity, Station(la, lo))
                Store.saveInterval(this@MainActivity, h)
                Store.saveEnabled(this@MainActivity, enabled.isChecked)
                PropWorker.schedule(this@MainActivity)
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "Send test notification"
            setOnClickListener {
                Notifier.post(this@MainActivity, 99, Finding("Test", true, "PropWatch test",
                    "Notifications are working. Real alerts look like this."))
            }
        })
        root.addView(TextView(this).apply {
            textSize = 12f; alpha = 0.6f; setPadding(0, dp(16), 0, 0)
            text = "Alerts only fire when a threshold is met: tropo ducting forecast along the North Sea paths " +
                "or a live cluster of ≥700 km spots, Kp ≥ 6- (or a G2 watch), 2m Es in progress, or a major " +
                "meteor shower peak (heads-up). The tropo forecast is a model estimate, not Hepburn's index."
        })

        setContentView(ScrollView(this).apply { addView(root) })
        render(Store.loadReport(this))
    }

    private fun section(t: String) = TextView(this).apply {
        text = t; textSize = 16f; setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(20), 0, dp(6))
    }

    private fun link(label: String, url: String) = Button(this).apply {
        text = label
        isAllCaps = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun number(hint: String, value: String, decimal: Boolean = true) = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED else InputType.TYPE_CLASS_NUMBER
    }

    private fun checkNow() {
        checkBtn.isEnabled = false
        checkBtn.text = "Checking…"
        val station = Store.station(this)
        Thread {
            val r = Checker(station).run()
            Store.saveReport(this, r)
            Notifier.handle(this, r)
            runOnUiThread {
                checkBtn.isEnabled = true
                checkBtn.text = "Check now"
                render(r)
            }
        }.start()
    }

    private fun render(r: Report?) {
        results.removeAllViews()
        if (r == null) {
            status.text = "No checks yet — tap Check now."
            return
        }
        val alerts = r.findings.count { it.alert && !it.lowGrade }
        status.text = "Last check ${Checker.ukTime(r.timeMs)} UK · " +
            if (alerts > 0) "$alerts alert${if (alerts > 1) "s" else ""}" else "conditions normal"
        for (f in r.findings) results.addView(card(f))
    }

    private fun card(f: Finding): View {
        val colour = when {
            f.alert && !f.lowGrade -> Color.rgb(0x1E, 0x9E, 0x6A)
            f.alert -> Color.rgb(0xD9, 0x8E, 0x04)
            f.headline == "Couldn't check" -> Color.rgb(0xB0, 0x3A, 0x2E)
            else -> Color.rgb(0x88, 0x88, 0x88)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setStroke(dp(2), colour)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(6), 0, dp(6)) }
            addView(TextView(context).apply {
                text = f.mode.uppercase(); textSize = 11f; setTextColor(colour); setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply { text = f.headline; textSize = 15f; setPadding(0, dp(2), 0, dp(2)) })
            if (f.detail.isNotBlank()) addView(TextView(context).apply { text = f.detail; textSize = 13f; alpha = 0.8f })
        }
    }
}
