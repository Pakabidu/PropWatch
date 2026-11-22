package uk.dx.propwatch

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure maths helpers: no Android dependencies, so they can be unit-tested on a plain JVM. */
object Geo {

    /** Maidenhead locator (4 or 6 chars) to the centre of that square, as (lat, lon). */
    fun locToLatLon(loc: String?): Pair<Double, Double>? {
        val l = loc?.trim()?.uppercase() ?: return null
        if (l.length < 4) return null
        val a = l[0] - 'A'
        val b = l[1] - 'A'
        if (a !in 0..17 || b !in 0..17) return null
        if (!l[2].isDigit() || !l[3].isDigit()) return null
        var lon = a * 20.0 - 180.0 + (l[2] - '0') * 2.0
        var lat = b * 10.0 - 90.0 + (l[3] - '0') * 1.0
        if (l.length >= 6 && l[4] in 'A'..'X' && l[5] in 'A'..'X') {
            lon += (l[4] - 'A') * (2.0 / 24.0) + 1.0 / 24.0
            lat += (l[5] - 'A') * (1.0 / 24.0) + 0.5 / 24.0
        } else {
            lon += 1.0
            lat += 0.5
        }
        return lat to lon
    }

    /** (lat, lon) to a 6-character Maidenhead locator, e.g. 52.98, -0.02 -> IO92XX. */
    fun latLonToLoc(lat: Double, lon: Double): String {
        val lo = (lon + 180.0).coerceIn(0.0, 359.9999)
        val la = (lat + 90.0).coerceIn(0.0, 179.9999)
        val sb = StringBuilder()
        sb.append('A' + (lo / 20).toInt()).append('A' + (la / 10).toInt())
        sb.append('0' + ((lo % 20) / 2).toInt()).append('0' + (la % 10).toInt())
        sb.append('a' + ((lo % 2) * 12).toInt()).append('a' + ((la % 1) * 24).toInt())
        return sb.toString()
    }

    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun compass(deg: Double): String {
        val names = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        return names[((deg / 22.5).roundToInt()) % 16]
    }

    /** Radio refractivity N from temperature (°C), pressure (hPa) and relative humidity (%). */
    fun refractivity(tC: Double, pHpa: Double, rh: Double): Double {
        val tK = tC + 273.15
        val es = 6.1094 * exp(17.625 * tC / (tC + 243.04))
        val e = rh / 100.0 * es
        return 77.6 / tK * (pHpa + 4810.0 * e / tK)
    }

    data class Level(val hM: Double, val tC: Double, val pHpa: Double, val rh: Double)

    /**
     * Steepest refractivity gradient (N-units per km) between neighbouring levels below [topM].
     * -157 N/km or lower means a duct; -79 to -157 is super-refraction; around -40 is normal.
     */
    fun minGradient(levels: List<Level>, topM: Double = 2500.0): Double? {
        val ok = levels.filter { !it.hM.isNaN() && !it.tC.isNaN() && !it.pHpa.isNaN() && !it.rh.isNaN() }
            .sortedBy { it.hM }
        var best: Double? = null
        for (i in 0 until ok.size - 1) {
            val a = ok[i]
            val b = ok[i + 1]
            if (b.hM > topM) break
            val dh = b.hM - a.hM
            if (dh < 20.0) continue
            val g = (refractivity(b.tC, b.pHpa, b.rh) - refractivity(a.tC, a.pHpa, a.rh)) / (dh / 1000.0)
            if (best == null || g < best) best = g
        }
        return best
    }

    /** Rough 0–10 lift estimate on a Hepburn-like scale: -157 N/km (ducting) maps to 5. */
    fun liftIndex(gradient: Double): Double = ((-gradient - 57.0) / 20.0).coerceIn(0.0, 10.0)

    fun parseUtc(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return try {
            val t = s.trim().replace(' ', 'T').removeSuffix("Z").take(19)
            LocalDateTime.parse(t).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    /** Major meteor showers (approximate peak date, UTC). */
    val showers = listOf(
        Triple("Quadrantids", 1, 4),
        Triple("Perseids", 8, 13),
        Triple("Geminids", 12, 14),
    )

    /** Returns the shower whose peak day falls within roughly the next 24 hours, if any. */
    fun showerNear(nowMs: Long): String? {
        val day = 24 * 3600 * 1000L
        val year = LocalDateTime.ofEpochSecond(nowMs / 1000, 0, ZoneOffset.UTC).year
        for ((name, m, d) in showers) {
            for (y in listOf(year - 1, year, year + 1)) {
                val start = LocalDate.of(y, m, d).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                if (nowMs in (start - day) until (start + day)) return name
            }
        }
        return null
    }
}
