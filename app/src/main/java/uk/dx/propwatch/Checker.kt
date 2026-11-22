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
class Checker(private val home: Station) {

    companion object {
        // Aurora: Kp 5.67 is "6-" on NOAA's scale, i.e. G2.
        const val KP_ALERT = 5.67

        // Tropo model: -157 N/km is the ducting threshold.
        const val DUCT_GRADIENT = -157.0
        const val MIN_DUCT_HOURS = 2

        // Live spots (PSKReporter). Aircraft scatter on FT8 reaches ~800 km from the UK,
        // so a single 700 km spot is not enough on its own.
        const val TROPO_KM = 700.0
        const val TROPO_PAIRS_2M = 5
        const val TROPO_LONG_KM = 1000.0
        const val TROPO_LONG_PAIRS = 2
        const val TROPO_PAIRS_70CM = 2
        const val ES_MIN_KM = 1000.0
        const val ES_MAX_KM = 2500.0
        const val ES_PAIRS = 2

        // 6m: single-hop Es is 700-2500 km; multi-hop Es / F2 / TEP beyond 3500 km is the real prize.
        const val SIXM_ES_MIN_KM = 700.0
        const val SIXM_ES_MAX_KM = 2500.0
        const val SIXM_ES_PAIRS = 3
        const val SIXM_DX_KM = 3500.0
        const val SIXM_DX_PAIRS = 2

        const val KP_OBS = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
        const val KP_FC = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json"
        const val SWPC_ALERTS = "https://services.swpc.noaa.gov/products/alerts.json"
        const val HAMQSL = "https://www.hamqsl.com/solarxml.php"

        /** Points along the North Sea paths from the Wash. */
        val PATH = listOf(
            PathPoint("The Wash", 52.98, -0.02, ukSide = true),
            PathPoint("Mid North Sea", 53.40, 2.50, ukSide = true),
            PathPoint("Dutch coast", 52.60, 4.60, ukSide = false),
            PathPoint("German Bight", 54.20, 7.50, ukSide = false),
            PathPoint("West Denmark", 56.00, 8.30, ukSide = false),
        )

        // PSKReporter asks for at most one query every 5 minutes. One query covers 6m-70cm,
        // is shared by all three live checks, and is reused for 5 minutes ("Check now" taps included).
        private const val PSK_MIN_GAP_MS = 5 * 60_000L
        private const val PSK_BACKOFF_MS = 15 * 60_000L
        private const val PSK_STALE_OK_MS = 30 * 60_000L
        private val pskLock = Any()
        private var pskCache: Pair<Long, List<Spot>>? = null
        private var pskBackoffUntil = 0L

        private val UK = ZoneId.of("Europe/London")
        private val FMT = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK)
        fun ukTime(ms: Long): String = FMT.format(Instant.ofEpochMilli(ms).atZone(UK))
    }

    class HttpError(val code: Int, val retryAfterS: Long?, msg: String) : IOException(msg)

    data class PathPoint(val name: String, val lat: Double, val lon: Double, val ukSide: Boolean)

    fun run(): Report {
        val now = System.currentTimeMillis()
        val findings = listOf(
            safe("Aurora") { aurora(now) },
            safe("Tropo forecast") { tropoForecast(now) },
            safe("HF bands") { hfBands(now) },
            safe("Live 6m spots") { live6m(now) },
            safe("Live 2m spots") { liveSpots(now, "2m", 144_000_000L, 146_000_000L) },
            safe("Live 70cm spots") { liveSpots(now, "70cm", 432_000_000L, 433_000_000L) },
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

        val kpNow = latest?.let { "Kp %.2f (%s)".format(it.second, ukTime(it.first)) } ?: "Kp n/a"
        val fcTxt = fcMax?.let { "max %.2f next 24 h".format(it) } ?: "no forecast"
        if (!alert) {
            return Finding("Aurora", false, "Quiet — $kpNow, $fcTxt", "No G2 watch. Threshold is Kp ≥ 6-.")
        }
        val timing = when {
            observedHigh -> "now"
            firstHigh != null -> "from ~${ukTime(firstHigh.first)} UK time"
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
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=" + PATH.joinToString(",") { "%.2f".format(Locale.US, it.lat) } +
            "&longitude=" + PATH.joinToString(",") { "%.2f".format(Locale.US, it.lon) } +
            "&hourly=" + vars.joinToString(",") +
            "&forecast_days=2&timezone=GMT"
        val body = get(url).trim()
        val locs: List<JSONObject> = if (body.startsWith("[")) {
            val a = JSONArray(body); (0 until a.length()).map { a.getJSONObject(it) }
        } else listOf(JSONObject(body))
        if (locs.size != PATH.size) throw IOException("Weather model returned ${locs.size} points")

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

        val hours = grads.flatMap { it.keys }.toSortedSet()
        val ductHours = mutableListOf<Long>()
        val farDucting = mutableSetOf<Int>()
        var peak = 0.0
        for (t in hours) {
            val ducting = PATH.indices.filter { (grads[it][t] ?: 0.0) <= DUCT_GRADIENT }
            val ukSide = ducting.any { PATH[it].ukSide }
            val farSide = ducting.filter { !PATH[it].ukSide }
            // Strong lift right over the Wash and the adjacent sea also counts.
            val overHome = (grads[0][t] ?: 0.0) <= DUCT_GRADIENT && (grads[1][t] ?: 0.0) <= DUCT_GRADIENT
            if ((ukSide && farSide.isNotEmpty()) || overHome) {
                ductHours += t
                farDucting += farSide
            }
            for (g in grads) g[t]?.let { peak = maxOf(peak, Geo.liftIndex(it)) }
        }

        val anticyclone = homeMsl?.let { it >= 1030.0 } == true && homeWind?.let { it < 15.0 } == true
        val wx = buildString {
            homeMsl?.let { append("MSL %.0f hPa".format(it)) }
            homeWind?.let { append(", wind %.0f km/h".format(it)) }
            if (anticyclone) append(" (classic anticyclone)")
        }
        val alert = ductHours.size >= MIN_DUCT_HOURS
        if (!alert) {
            return Finding("Tropo forecast", false,
                "No ducting forecast on North Sea paths (peak est. index %.1f)".format(peak),
                "$wx. Model estimate from Open-Meteo profiles; check Hepburn's maps for detail.")
        }
        val dirs = if (farDucting.isEmpty()) "E across the North Sea" else farDucting.sorted().joinToString(", ") {
            val p = PATH[it]
            val b = Geo.bearing(home.lat, home.lon, p.lat, p.lon)
            "${p.name} (${Geo.compass(b)} %.0f°)".format(b)
        }
        val conf = when {
            ductHours.size >= 6 && anticyclone -> "high"
            ductHours.size >= 4 || anticyclone -> "medium"
            else -> "low–medium"
        }
        val detail = "Ducting ${ukTime(ductHours.first())}–${ukTime(ductHours.last())} UK time " +
            "(${ductHours.size} h). Paths: $dirs. Peak est. index %.1f. $wx.\n".format(peak) +
            "70cm usually beats 2m in a duct. Confidence $conf.\n" +
            "Source: Open-Meteo model profiles (a proxy for Hepburn — confirm on dxinfocentre.com)."
        return Finding("Tropo forecast", true, "Tropo duct forecast — 2m & 70cm to the E/NE", detail)
    }

    // ---------------------------------------------------------------- live spots (PSKReporter)

    data class Spot(val a: String, val b: String, val km: Double, val la: Double, val loa: Double,
                    val lb: Double, val lob: Double, val mode: String, val hz: Long)

    /** Spots in [loHz, hiHz] from the shared, rate-limited PSKReporter query. */
    private fun bandSpots(now: Long, loHz: Long, hiHz: Long): List<Spot> =
        vhfSpots(now).filter { it.hz in loHz..hiHz }

    private fun vhfSpots(now: Long): List<Spot> = synchronized(pskLock) {
        val cached = pskCache
        if (cached != null && now - cached.first < PSK_MIN_GAP_MS) return cached.second
        if (now < pskBackoffUntil) {
            if (cached != null && now - cached.first < PSK_STALE_OK_MS) return cached.second
            throw IOException("PSKReporter is rate-limiting (HTTP 429). Next try after ${ukTime(pskBackoffUntil)} UK.")
        }
        try {
            val spots = fetchSpots("50000000-433000000")
            pskCache = now to spots
            spots
        } catch (e: HttpError) {
            if (e.code != 429 && e.code != 503) throw e
            pskBackoffUntil = now + maxOf(PSK_BACKOFF_MS, (e.retryAfterS ?: 0L) * 1000L)
            if (cached != null && now - cached.first < PSK_STALE_OK_MS) return cached.second
            throw IOException("PSKReporter is rate-limiting (HTTP ${e.code}). Next try after ${ukTime(pskBackoffUntil)} UK.")
        }
    }

    private val nonTropoModes = setOf("MSK144", "FSK441", "Q65", "JT65", "JT4", "JT6M", "ISCAT")

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
                if (ra != null && sa != null && rx.isNotBlank() && tx.isNotBlank() && mode !in nonTropoModes) {
                    val d = Geo.km(ra.first, ra.second, sa.first, sa.second)
                    out += Spot(rx.uppercase(), tx.uppercase(), d, ra.first, ra.second, sa.first, sa.second, mode, hz)
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun inUkBenelux(lat: Double, lon: Double) = lat in 49.8..59.5 && lon in -8.5..7.3
    private fun inNwEurope(lat: Double, lon: Double) = lat in 45.0..63.0 && lon in -11.0..16.0
    private fun pairKey(s: Spot) = listOf(s.a, s.b).sorted().joinToString("–")

    private fun liveSpots(now: Long, band: String, loHz: Long, hiHz: Long): Finding {
        val spots = bandSpots(now, loHz, hiHz)
        val month = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).monthValue
        val esSeason = month in 5..8 || month == 12

        val tropo = spots.filter {
            it.km >= TROPO_KM && it.km <= 2800 && (inUkBenelux(it.la, it.loa) || inUkBenelux(it.lb, it.lob))
        }
        val tropoPairs = tropo.map(::pairKey).toSet()
        val longPairs = tropo.filter { it.km >= TROPO_LONG_KM }.map(::pairKey).toSet()

        val es = if (band == "2m" && esSeason) spots.filter {
            it.km in ES_MIN_KM..ES_MAX_KM && (inNwEurope(it.la, it.loa) || inNwEurope(it.lb, it.lob))
        } else emptyList()
        val esPairs = es.map(::pairKey).toSet()

        val mode = "Live $band spots"
        val best = (tropo + es).maxByOrNull { it.km }
        val bestTxt = best?.let { "best ${it.a}–${it.b} %.0f km (${it.mode})".format(it.km) } ?: ""

        // In season, a cluster of 1000–2500 km spots is far more likely Es than tropo.
        if (esPairs.size >= ES_PAIRS) {
            val mid = es.maxByOrNull { it.km }!!
            val far = if (inUkBenelux(mid.la, mid.loa)) mid.lb to mid.lob else mid.la to mid.loa
            val b = Geo.bearing(home.lat, home.lon, far.first, far.second)
            return Finding(mode, true, "2m sporadic E in progress",
                "${esPairs.size} paths 1000–2500 km in the last 30 min; $bestTxt. " +
                    "Look ${Geo.compass(b)} (%.0f°) — Es moves fast, check now.\nSource: PSKReporter.".format(b))
        }
        val tropoAlert = if (band == "2m") tropoPairs.size >= TROPO_PAIRS_2M || longPairs.size >= TROPO_LONG_PAIRS
        else tropoPairs.size >= TROPO_PAIRS_70CM
        if (tropoAlert) {
            val farEnds = tropo.map { if (inUkBenelux(it.la, it.loa) && !inUkBenelux(it.lb, it.lob)) it.lb to it.lob else it.la to it.loa }
            val dirs = farEnds.map { Geo.compass(Geo.bearing(home.lat, home.lon, it.first, it.second)) }
                .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).joinToString(", ") { it.key }
            return Finding(mode, true, "$band tropo opening in progress",
                "${tropoPairs.size} paths ≥700 km touching UK/Benelux in the last 30 min; $bestTxt. Directions: $dirs.\n" +
                    "Source: PSKReporter (digital modes; MS/EME modes excluded).")
        }
        return Finding(mode, false,
            if (spots.isEmpty()) "No $band reports in the last 30 min"
            else "Normal — ${tropoPairs.size} paths ≥700 km${if (bestTxt.isNotEmpty()) ", $bestTxt" else ""}",
            "${spots.size} $band reports checked.")
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

    // ---------------------------------------------------------------- live 6m

    private fun live6m(now: Long): Finding {
        val spots = bandSpots(now, 50_000_000L, 50_500_000L)
        val es = spots.filter {
            it.km in SIXM_ES_MIN_KM..SIXM_ES_MAX_KM && (inNwEurope(it.la, it.loa) || inNwEurope(it.lb, it.lob))
        }
        val dx = spots.filter { it.km >= SIXM_DX_KM && (inUkBenelux(it.la, it.loa) || inUkBenelux(it.lb, it.lob)) }
        val esPairs = es.map(::pairKey).toSet()
        val dxPairs = dx.map(::pairKey).toSet()
        val n0nbh = try {
            listOfNotNull(solar["vhf/E-Skip/europe_6m"]?.let { "6m $it" }, solar["vhf/E-Skip/europe_4m"]?.let { "4m $it" })
                .joinToString(", ").takeIf { it.isNotEmpty() }?.let { " N0NBH Europe Es: $it." } ?: ""
        } catch (_: Exception) { "" }
        fun look(s: Spot): String {
            val far = if (inUkBenelux(s.la, s.loa)) s.lb to s.lob else s.la to s.loa
            val b = Geo.bearing(home.lat, home.lon, far.first, far.second)
            return "${Geo.compass(b)} (%.0f°)".format(b)
        }
        fun best(l: List<Spot>) = l.maxByOrNull { it.km }!!.let { "best ${it.a}–${it.b} %.0f km (${it.mode})".format(it.km) }
        val mode = "Live 6m spots"
        if (dxPairs.size >= SIXM_DX_PAIRS) {
            val far = dx.maxByOrNull { it.km }!!
            return Finding(mode, true, "6m long-haul DX open (multi-hop Es / F2)",
                "${dxPairs.size} paths ≥3500 km touching UK/Benelux in the last 30 min; ${best(dx)}. " +
                    "Look ${look(far)}.$n0nbh\nSource: PSKReporter.")
        }
        if (esPairs.size >= SIXM_ES_PAIRS) {
            return Finding(mode, true, "6m sporadic E in progress",
                "${esPairs.size} paths 700–2500 km in NW Europe in the last 30 min; ${best(es)}. " +
                    "Look ${look(es.maxByOrNull { it.km }!!)}. Try 50.313 FT8 or 50.150 SSB.$n0nbh\nSource: PSKReporter.")
        }
        return Finding(mode, false,
            if (spots.isEmpty()) "No 6m reports in the last 30 min"
            else "Normal — ${esPairs.size} Es-range paths${if (es.isNotEmpty()) ", ${best(es)}" else ""}",
            "${spots.size} 6m reports checked.$n0nbh")
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
