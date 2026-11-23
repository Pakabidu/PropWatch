package uk.dx.propwatch

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One propagation mode's verdict. alert = threshold met; lowGrade = heads-up only (meteor showers). */
data class Finding(
    val mode: String,
    val alert: Boolean,
    val headline: String,
    val detail: String,
    val lowGrade: Boolean = false,
)

data class Report(val timeMs: Long, val findings: List<Finding>)

data class Station(val lat: Double, val lon: Double)

/**
 * Runs every check. All network calls are blocking, so call this off the main thread.
 * Thresholds mirror the original monitoring brief; tune them in the constants below.
 */
/**
 * @param bands the tropo bands to check live (the user's choice, see [Store.visibleTropoBands]).
 *   Each gets its own "Live … spots" reading; the tropo forecast names them too.
 * @param cb also watch citizens band (27 MHz) for skip.
 */
class Checker(private val home: Station, private val bands: List<TropoBand> = TropoBands.CORE,
              private val cb: Boolean = true) {

    companion object {
        // Aurora: Kp 5.67 is "6-" on NOAA's scale, i.e. G2.
        const val KP_ALERT = 5.67

        // Tropo model: ducting (-157 N/km, see TropoPaths) for at least this many forecast hours.
        const val MIN_DUCT_HOURS = 2

        // Live-spot thresholds per band live in SpotRules.

        const val KP_OBS = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
        const val KP_FC = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json"
        const val SWPC_ALERTS = "https://services.swpc.noaa.gov/products/alerts.json"
        const val HAMQSL = "https://www.hamqsl.com/solarxml.php"

        // PSKReporter asks for at most one query every 5 minutes. One query covers every
        // chosen band's window, is shared by all the live checks, and is reused for 5 minutes
        // ("Check now" taps included).
        private const val PSK_MIN_GAP_MS = 5 * 60_000L
        private const val PSK_BACKOFF_MS = 15 * 60_000L
        private const val PSK_STALE_OK_MS = 30 * 60_000L
        private val pskLock = Any()
        private var pskCache: Triple<Long, LongRange, List<Spot>>? = null   // time, Hz range, spots
        private var pskBackoffUntil = 0L
        private var cbCache: Pair<Long, List<Spot>>? = null   // CB has its own small query
        const val CB_MODE = "CB 11m"

        private val FMT = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK)
        /** Day and time in the phone's own time zone, e.g. "Sun 14:32". */
        fun localTime(ms: Long): String = FMT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    }

    class HttpError(val code: Int, val retryAfterS: Long?, msg: String) : IOException(msg)


    fun run(): Report {
        val now = System.currentTimeMillis()
        val findings = listOf(
            safe("Aurora") { aurora(now) },
            safe("Tropo forecast") { tropoForecast(now) },
            safe("HF bands") { hfBands(now) },
        ) + bands.map { b -> safe("Live ${b.label} spots") { liveBand(now, b) } } +
            (if (cb) listOf(safe(CB_MODE) { citizensBand(now) }) else emptyList()) + listOf(
            safe("Meteor scatter") { meteors(now) },
        )
        return Report(now, findings)
    }

    private fun safe(mode: String, block: () -> Finding): Finding = try {
        block()
    } catch (e: Exception) {
        Finding(mode, false, "Couldn't check", e.message ?: e.javaClass.simpleName)
    }

    // ---------------------------------------------------------------- network

    private fun get(url: String, timeoutMs: Int = 30_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("User-Agent", "PropWatch/1.0 (amateur radio propagation monitor)")
        try {
            val code = c.responseCode
            if (code !in 200..299) throw HttpError(code, c.getHeaderField("Retry-After")?.trim()?.toLongOrNull(),
                "HTTP $code from ${c.url.host}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** SWPC serves both array-of-arrays (header row first) and array-of-objects; accept either. */
    private fun rows(json: String): List<Map<String, String>> {
        val arr = JSONArray(json)
        if (arr.length() == 0) return emptyList()
        val out = mutableListOf<Map<String, String>>()
        val first = arr.get(0)
        if (first is JSONArray) {
            val hdr = (0 until first.length()).map { first.optString(it).lowercase() }
            for (i in 1 until arr.length()) {
                val r = arr.optJSONArray(i) ?: continue
                out += hdr.indices.associate { hdr[it] to r.optString(it) }
            }
        } else {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val m = mutableMapOf<String, String>()
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    m[k.lowercase()] = o.optString(k)
                }
                out += m
            }
        }
        return out
    }

    private fun kpRows(json: String): List<Triple<Long, Double, String>> = rows(json).mapNotNull { r ->
        val t = Geo.parseUtc(r["time_tag"])
        val kp = (r["kp"] ?: r["kp_index"] ?: r["estimated_kp"])?.toDoubleOrNull()
        if (t != null && kp != null) Triple(t, kp, r["observed"] ?: "") else null
    }

    // ---------------------------------------------------------------- aurora

    private fun aurora(now: Long): Finding {
        val hour = 3600_000L
        val obs = kpRows(get(KP_OBS))
        val latest = obs.maxByOrNull { it.first }
        val recentMax = obs.filter { it.first >= now - 6 * hour }.maxOfOrNull { it.second }

        val fc = kpRows(get(KP_FC)).filter { it.first > now - 3 * hour && it.first <= now + 24 * hour }
        val fcMax = fc.maxOfOrNull { it.second }
        val firstHigh = fc.filter { it.second >= KP_ALERT }.minByOrNull { it.first }

        // G2+ watches issued in the last 48 h.
        var watch: String? = null
        try {
            val alerts = JSONArray(get(SWPC_ALERTS))
            val re = Regex("WATCH: Geomagnetic Storm Category G([2-5])")
            for (i in 0 until alerts.length()) {
                val o = alerts.optJSONObject(i) ?: continue
                val issued = Geo.parseUtc(o.optString("issue_datetime")) ?: continue
                if (issued < now - 48 * hour) continue
                val m = re.find(o.optString("message")) ?: continue
                val g = "G" + m.groupValues[1]
                if (watch == null || g > watch) watch = g
            }
        } catch (_: Exception) {
            // Watches are a bonus; Kp alone is enough to decide.
        }

        val observedHigh = (recentMax ?: 0.0) >= KP_ALERT
        val forecastHigh = (fcMax ?: 0.0) >= KP_ALERT
        val alert = observedHigh || forecastHigh || watch != null

        val kpNow = latest?.let { "Kp %.2f (%s)".format(it.second, localTime(it.first)) } ?: "Kp n/a"
        val fcTxt = fcMax?.let { "max %.2f next 24 h".format(it) } ?: "no forecast"
        if (!alert) {
            return Finding("Aurora", false, "Quiet — $kpNow, $fcTxt", "No G2 watch. Threshold is Kp ≥ 6-.")
        }
        val timing = when {
            observedHigh -> "now"
            firstHigh != null -> "from ~${localTime(firstHigh.first)}"
            else -> "per SWPC $watch watch"
        }
        val strong = maxOf(recentMax ?: 0.0, fcMax ?: 0.0) >= 7.0
        val bands = if (strong) "2m, possibly 70cm" else "2m"
        val detail = "$kpNow, $fcTxt${watch?.let { ", SWPC $it watch" } ?: ""}.\n" +
            "Beam N–NE (0–40°) for GM/LA/SM/OZ; CW/SSB, signals will be rough. Best late afternoon and evening.\n" +
            "Source: NOAA SWPC."
        return Finding("Aurora", true, "Aurora $timing — $bands", detail)
    }

    // ---------------------------------------------------------------- tropo (model)

    private fun tropoForecast(now: Long): Finding {
        val levels = listOf(1000, 975, 950, 925, 900, 850)
        val vars = mutableListOf("pressure_msl", "surface_pressure", "temperature_2m",
            "relative_humidity_2m", "wind_speed_10m")
        for (l in levels) {
            vars += "temperature_${l}hPa"; vars += "relative_humidity_${l}hPa"; vars += "geopotential_height_${l}hPa"
        }
        // The station plus two rings of points (250 and 500 km) on the eight compass bearings.
        val points = TropoPaths.points(home.lat, home.lon)
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=" + points.joinToString(",") { "%.2f".format(Locale.US, it.lat) } +
            "&longitude=" + points.joinToString(",") { "%.2f".format(Locale.US, it.lon) } +
            "&hourly=" + vars.joinToString(",") +
            "&forecast_days=2&timezone=GMT"
        val body = get(url).trim()
        val locs: List<JSONObject> = if (body.startsWith("[")) {
            val a = JSONArray(body); (0 until a.length()).map { a.getJSONObject(it) }
        } else listOf(JSONObject(body))
        if (locs.size != points.size) throw IOException("Weather model returned ${locs.size} of ${points.size} points")

        // gradient[point][hourMs]
        val grads = mutableListOf<Map<Long, Double>>()
        var homeMsl: Double? = null
        var homeWind: Double? = null
        for ((pi, loc) in locs.withIndex()) {
            val h = loc.getJSONObject("hourly")
            val times = h.getJSONArray("time")
            val elev = loc.optDouble("elevation", 0.0).let { if (it.isNaN()) 0.0 else it }
            fun v(name: String, i: Int): Double = h.optJSONArray(name)?.optDouble(i, Double.NaN) ?: Double.NaN
            val m = mutableMapOf<Long, Double>()
            for (i in 0 until times.length()) {
                val t = Geo.parseUtc(times.getString(i)) ?: continue
                if (t < now - 3600_000L || t > now + 24 * 3600_000L) continue
                val sp = v("surface_pressure", i)
                val prof = mutableListOf(Geo.Level(elev + 2.0, v("temperature_2m", i), sp, v("relative_humidity_2m", i)))
                for (l in levels) {
                    if (!sp.isNaN() && l >= sp - 1.0) continue // level is at or below the ground
                    prof += Geo.Level(v("geopotential_height_${l}hPa", i), v("temperature_${l}hPa", i), l.toDouble(),
                        v("relative_humidity_${l}hPa", i))
                }
                Geo.minGradient(prof)?.let { m[t] = it }
                if (pi == 0 && homeMsl == null) {
                    homeMsl = v("pressure_msl", i).takeIf { !it.isNaN() }
                    homeWind = v("wind_speed_10m", i).takeIf { !it.isNaN() }
                }
            }
            grads += m
        }

        val result = TropoPaths.evaluate(grads)
        val ductHours = result.ductHours
        var peak = 0.0
        for (g in grads) for (v in g.values) peak = maxOf(peak, Geo.liftIndex(v))

        val anticyclone = homeMsl?.let { it >= 1030.0 } == true && homeWind?.let { it < 15.0 } == true
        val wx = buildString {
            homeMsl?.let { append("MSL %.0f hPa".format(it)) }
            homeWind?.let { append(", wind %.0f km/h".format(it)) }
            if (anticyclone) append(" (classic anticyclone)")
        }
        val alert = ductHours.size >= MIN_DUCT_HOURS
        if (!alert) {
            return Finding("Tropo forecast", false,
                "No ducting forecast around you (peak est. index %.1f)".format(peak),
                "$wx. Model estimate from Open-Meteo profiles at your station and 250/500 km out in 8 directions; " +
                    "check Hepburn's maps for your region for detail.")
        }
        val dirs = result.directions.joinToString(", ") {
            "${Geo.compass(it.bearing.toDouble())} to %.0f km (${it.hours} h)".format(it.reachKm)
        }
        val heading = result.directions.take(2).joinToString("/") { Geo.compass(it.bearing.toDouble()) }
        val conf = when {
            ductHours.size >= 6 && anticyclone -> "high"
            ductHours.size >= 4 || anticyclone -> "medium"
            else -> "low–medium"
        }
        val detail = "Ducting ${localTime(ductHours.first())}–${localTime(ductHours.last())} " +
            "(${ductHours.size} h). Paths: $dirs. Peak est. index %.1f. $wx.\n".format(peak) +
            ductNote() + " Confidence $conf.\n" +
            "Source: Open-Meteo model profiles (an estimate; confirm on Hepburn's maps for your region at dxinfocentre.com)."
        return Finding("Tropo forecast", true, "Tropo duct forecast — ${ductBandsText()} to the $heading", detail)
    }

    /** The chosen bands a duct favours most, by [TropoBand.ductSensitivityFactor]: "23cm, 70cm & 2m". */
    private fun ductBandsText(): String {
        val top = bands.sortedByDescending { it.ductSensitivityFactor }.take(3).map { it.label }
        return when (top.size) {
            0 -> "VHF/UHF"
            1 -> top[0]
            else -> top.dropLast(1).joinToString(", ") + " & " + top.last()
        }
    }

    /** Which chosen bands a duct traps most easily, e.g. "Shallow ducts trap 23cm (×4.3) and 70cm (×2.1) before 2m." */
    private fun ductNote(): String {
        val sorted = bands.sortedByDescending { it.ductSensitivityFactor }
        if (sorted.size < 2) return "Higher bands are trapped by shallower ducts."
        val lead = sorted.take(2).joinToString(" and ") { "${it.label} (×%.1f)".format(it.ductSensitivityFactor) }
        return "Shallow ducts trap $lead before ${sorted.last().label}."
    }

    // ---------------------------------------------------------------- live spots (PSKReporter)

    /**
     * Spots for one band from the shared, rate-limited PSKReporter query. The query spans
     * every chosen band's window; if the bands changed and the last query (under 5 minutes
     * old) doesn't cover this one, it waits for the next check rather than query again.
     */
    private fun bandSpots(now: Long, band: TropoBand): List<Spot> {
        val want = SpotRules.queryRangeHz(bands) ?: return emptyList()
        val (range, spots) = vhfSpots(now, want)
        val needed = SpotRules.windowsHz(band)
        if (needed.any { it.first < range.first || it.last > range.last }) {
            throw IOException("PSKReporter was queried under 5 minutes ago; ${band.label} is included from the next check.")
        }
        return spots.filter { SpotRules.inBand(it, band) }
    }

    private fun vhfSpots(now: Long, want: LongRange): Pair<LongRange, List<Spot>> = synchronized(pskLock) {
        val cached = pskCache
        fun covers(c: Triple<Long, LongRange, List<Spot>>) = c.second.first <= want.first && c.second.last >= want.last
        if (cached != null && now - cached.first < PSK_MIN_GAP_MS) return cached.second to cached.third
        if (now < pskBackoffUntil) {
            if (cached != null && now - cached.first < PSK_STALE_OK_MS && covers(cached)) return cached.second to cached.third
            throw IOException("PSKReporter is rate-limiting (HTTP 429). Next try after ${localTime(pskBackoffUntil)}.")
        }
        try {
            val spots = fetchSpots("${want.first}-${want.last}")
            pskCache = Triple(now, want, spots)
            want to spots
        } catch (e: HttpError) {
            if (e.code != 429 && e.code != 503) throw e
            pskBackoffUntil = now + maxOf(PSK_BACKOFF_MS, (e.retryAfterS ?: 0L) * 1000L)
            if (cached != null && now - cached.first < PSK_STALE_OK_MS && covers(cached)) return cached.second to cached.third
            throw IOException("PSKReporter is rate-limiting (HTTP ${e.code}). Next try after ${localTime(pskBackoffUntil)}.")
        }
    }

    private fun fetchSpots(frange: String): List<Spot> {
        val url = "https://retrieve.pskreporter.info/query?flowStartSeconds=-1800" +
            "&frange=$frange&rronly=1&noactive=1&appcontact=propwatch-android"
        val xml = get(url, 45_000)
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val out = mutableListOf<Spot>()
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.name == "receptionReport") {
                val rx = p.getAttributeValue(null, "receiverCallsign") ?: ""
                val tx = p.getAttributeValue(null, "senderCallsign") ?: ""
                val mode = (p.getAttributeValue(null, "mode") ?: "").uppercase()
                val hz = p.getAttributeValue(null, "frequency")?.trim()?.toLongOrNull() ?: -1L
                val ra = Geo.locToLatLon(p.getAttributeValue(null, "receiverLocator"))
                val sa = Geo.locToLatLon(p.getAttributeValue(null, "senderLocator"))
                if (ra != null && sa != null && rx.isNotBlank() && tx.isNotBlank()) {
                    val d = Geo.km(ra.first, ra.second, sa.first, sa.second)
                    out += Spot(rx.uppercase(), tx.uppercase(), d, ra.first, ra.second, sa.first, sa.second, mode, hz)
                }
            }
            ev = p.next()
        }
        return out
    }

    /** One band's live reading, judged by [SpotRules] relative to the station. */
    private fun liveBand(now: Long, band: TropoBand): Finding {
        val spots = bandSpots(now, band)
        val month = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).monthValue
        val v = SpotRules.evaluate(band, spots, home.lat, home.lon, month)
        val r = SpotRules.rule(band)
        val mode = "Live ${band.label} spots"
        val bestTxt = v.best?.let { "best ${it.a}–${it.b} %.0f km (${it.mode})".format(it.km) } ?: ""
        fun look(s: Spot): String {
            val far = SpotRules.farEnd(s, home.lat, home.lon)
            val b = Geo.bearing(home.lat, home.lon, far.first, far.second)
            return "${Geo.compass(b)} (%.0f°)".format(b)
        }
        val n0nbh = if (band.id != "6m" && band.id != "4m") "" else try {
            solar["vhf/E-Skip/europe_${band.id}"]?.let { " N0NBH Europe ${band.label} Es: $it." } ?: ""
        } catch (_: Exception) { "" }
        val near = "%.0f km".format(SpotRules.NEAR_KM)
        return when (v.kind) {
            SpotRules.Kind.DX -> Finding(mode, true, "${band.label} long-haul DX open (multi-hop Es / F2)",
                "${v.pairs} paths ≥%.0f km within $near of you in the last 30 min; $bestTxt. ".format(r.dxKm) +
                    "Look ${look(v.best!!)}.$n0nbh\nSource: PSKReporter.")
            SpotRules.Kind.ES -> Finding(mode, true, "${band.label} sporadic E in progress",
                "${v.pairs} paths %.0f–%.0f km in your area in the last 30 min; $bestTxt. ".format(r.esMinKm, r.esMaxKm) +
                    "Look ${look(v.best!!)} — Es moves fast, check now.$n0nbh\nSource: PSKReporter.")
            SpotRules.Kind.TROPO -> {
                val dirs = v.spots.map { s -> SpotRules.farEnd(s, home.lat, home.lon) }
                    .map { Geo.compass(Geo.bearing(home.lat, home.lon, it.first, it.second)) }
                    .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).joinToString(", ") { it.key }
                Finding(mode, true, "${band.label} tropo opening in progress",
                    "${v.pairs} path${if (v.pairs == 1) "" else "s"} ≥%.0f km within $near of you in the last 30 min; ".format(r.tropoKm) +
                        "$bestTxt. Directions: $dirs.\nSource: PSKReporter (digital modes; MS/EME modes excluded).")
            }
            SpotRules.Kind.QUIET -> Finding(mode, false,
                if (v.considered == 0) "No ${band.label} reports in the last 30 min"
                else if (r.tropoKm == null) "Normal — ${v.pairs} Es-range paths${if (bestTxt.isNotEmpty()) ", $bestTxt" else ""}"
                else "Normal — ${v.pairs} paths ≥%.0f km${if (bestTxt.isNotEmpty()) ", $bestTxt" else ""}".format(r.tropoKm),
                "${v.considered} ${band.label} reports checked.$n0nbh")
        }
    }

    // ---------------------------------------------------------------- citizens band (27 MHz)

    /** CB spots from PSKReporter: a small query of its own, cached and rate-limited like the VHF one. */
    private fun cbSpots(now: Long): List<Spot> = synchronized(pskLock) {
        val cached = cbCache
        if (cached != null && now - cached.first < PSK_MIN_GAP_MS) return cached.second
        if (now < pskBackoffUntil) {
            if (cached != null && now - cached.first < PSK_STALE_OK_MS) return cached.second
            throw IOException("PSKReporter is rate-limiting. Next try after ${localTime(pskBackoffUntil)}.")
        }
        try {
            val spots = fetchSpots("${CbRules.RANGE_HZ.first}-${CbRules.RANGE_HZ.last}")
            cbCache = now to spots
            spots
        } catch (e: HttpError) {
            if (e.code != 429 && e.code != 503) throw e
            pskBackoffUntil = now + maxOf(PSK_BACKOFF_MS, (e.retryAfterS ?: 0L) * 1000L)
            if (cached != null && now - cached.first < PSK_STALE_OK_MS) return cached.second
            throw IOException("PSKReporter is rate-limiting (HTTP ${e.code}). Next try after ${localTime(pskBackoffUntil)}.")
        }
    }

    /**
     * CB skip: live 27 MHz reports first; failing those, N0NBH's 6m sporadic-E report for
     * Europe (when 6m skips, 27 MHz always does) and its 10m rating (F2 DX on 10m carries
     * down to 11m).
     */
    private fun citizensBand(now: Long): Finding {
        var spotErr: String? = null
        val v = try { CbRules.evaluate(cbSpots(now), home.lat, home.lon) } catch (e: Exception) { spotErr = e.message; null }
        val s = try { solar } catch (_: Exception) { null }
        if (v == null && s == null) throw IOException("PSKReporter and N0NBH are both unavailable")
        val day = isDay(now)
        val ten = s?.get("band/12m-10m/${if (day) "day" else "night"}")
        val inEurope = home.lat in 34.0..72.0 && home.lon in -25.0..45.0
        val es6 = if (inEurope) s?.get("vhf/E-Skip/europe_6m") else null
        val es6Open = es6 != null && !es6.contains("closed", ignoreCase = true)
        val tenTxt = ten?.let { " 10m is rated $it (N0NBH)." } ?: ""
        val spotsTxt = when {
            v == null -> "Live CB reports unavailable: $spotErr"
            else -> "${v.considered} CB reports in the last 30 min (PSKReporter, mostly FT8 around 27.265 MHz)."
        }
        fun look(sp: Spot): String {
            val far = SpotRules.farEnd(sp, home.lat, home.lon)
            val b = Geo.bearing(home.lat, home.lon, far.first, far.second)
            return "${Geo.compass(b)} (%.0f°)".format(b)
        }
        val best = v?.best?.let { "best ${it.a}–${it.b} %.0f km (${it.mode})".format(it.km) } ?: ""
        return when {
            v?.kind == CbRules.Kind.DX -> Finding(CB_MODE, true, "CB DX skip open (F2)",
                "${v.pairs} paths over %.0f km touching your area in the last 30 min; $best. Look ${look(v.best!!)}.$tenTxt\n".format(CbRules.DX_KM) +
                    "Source: PSKReporter.")
            v?.kind == CbRules.Kind.SKIP -> Finding(CB_MODE, true, "CB skip open — sporadic E",
                "${v.pairs} paths over %.0f km touching your area in the last 30 min; $best. Look ${look(v.best!!)}. ".format(CbRules.SKIP_MIN_KM) +
                    "Es skip comes and goes fast.$tenTxt\nSource: PSKReporter.")
            es6Open -> Finding(CB_MODE, true, "CB skip likely — 6m sporadic E over Europe",
                "N0NBH reports \"$es6\" on 6m over Europe. When 6m skips, 27 MHz skips too, with hops of about 600–2000 km.$tenTxt\n$spotsTxt",
                lowGrade = true)
            ten.equals("Good", ignoreCase = true) -> Finding(CB_MODE, true, "CB DX likely — 10m rated Good",
                "N0NBH rates 12m–10m Good ${if (day) "by day" else "tonight"}; F2 openings on 10m usually reach down to 27 MHz.\n$spotsTxt",
                lowGrade = true)
            else -> Finding(CB_MODE, false, "No CB skip" + (ten?.let { " — 10m $it" } ?: ""),
                spotsTxt + if (v != null && v.pairs > 0) " ${v.pairs} long path${if (v.pairs == 1) "" else "s"}; $best." else "")
        }
    }

    /** Daytime at the station by local solar time: which N0NBH column applies. */
    private fun isDay(now: Long): Boolean {
        val utcH = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).let { it.hour + it.minute / 60.0 }
        val solarH = ((utcH + home.lon / 15.0) % 24 + 24) % 24
        return solarH in 6.0..18.0
    }

    // ---------------------------------------------------------------- HF + 6m/4m status (N0NBH)

    /** N0NBH's solar feed, fetched at most once per run (HF bands and 6m both use it). */
    private val solar: Map<String, String> by lazy {
        val p = Xml.newPullParser()
        p.setInput(StringReader(get(HAMQSL)))
        val out = mutableMapOf<String, String>()
        var key: String? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> key = when (p.name) {
                    "band" -> "band/${p.getAttributeValue(null, "name")}/${p.getAttributeValue(null, "time")}"
                    "phenomenon" -> "vhf/${p.getAttributeValue(null, "name")}/${p.getAttributeValue(null, "location")}"
                    else -> p.name
                }
                XmlPullParser.TEXT -> key?.let { k -> p.text.trim().takeIf { it.isNotEmpty() }?.let { out[k] = it } }
                XmlPullParser.END_TAG -> key = null
            }
            ev = p.next()
        }
        if (out.keys.none { it.startsWith("band/") }) throw IOException("No band data from hamqsl.com")
        out
    }

    private val hfGroups = listOf("80m-40m", "30m-20m", "17m-15m", "12m-10m")

    private fun hfBands(now: Long): Finding {
        val s = solar
        // Local solar time at the station decides whether the day or night column applies.
        val utcH = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).let { it.hour + it.minute / 60.0 }
        val solarH = ((utcH + home.lon / 15.0) % 24 + 24) % 24
        val day = solarH in 6.0..18.0
        fun row(t: String) = hfGroups.joinToString(" · ") { "${it.replace("m-", "–")} ${s["band/$it/$t"] ?: "?"}" }
        val now10 = s["band/12m-10m/${if (day) "day" else "night"}"]
        val wx = listOfNotNull(
            s["solarflux"]?.let { "SFI $it" }, s["sunspots"]?.let { "SSN $it" },
            s["kindex"]?.let { "K $it" }, s["aindex"]?.let { "A $it" },
            s["signalnoise"]?.let { "noise $it" }, s["geomagfield"]?.let { "geomag $it" },
        ).joinToString(", ")
        val other = if (day) "Night" else "Day"
        val detail = "$other: ${row(if (day) "night" else "day")}\n$wx.\n" +
            "160m behaves like 80m at night; 60m like 40m. Source: N0NBH (hamqsl.com)."
        // 10m/12m rated Good is the notable one; everything else is just information.
        val tenOpen = now10.equals("Good", ignoreCase = true)
        return Finding("HF bands", tenOpen,
            (if (tenOpen) "10m & 12m open — " else "") + "${if (day) "Day" else "Night"}: ${row(if (day) "day" else "night")}",
            detail, lowGrade = true)
    }

    // ---------------------------------------------------------------- meteors

    private fun meteors(now: Long): Finding {
        val s = Geo.showerNear(now)
            ?: return Finding("Meteor scatter", false, "No major shower peak within 24 h", "")
        return Finding("Meteor scatter", true, "$s peak within 24 h (heads-up)",
            "Good MS on 2m: MSK144 around 144.360, paths ~800–2000 km in all directions. Best pre-dawn.",
            lowGrade = true)
    }
}
