package uk.dx.propwatch

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Band configuration for tropospheric ducting: VHF, UHF and microwave amateur bands
 * only (HF, 160m–10m, is deliberately excluded: it doesn't duct in the troposphere).
 *
 * Pure Kotlin with no Android dependencies, so it can be unit-tested on a plain JVM.
 * Settings persistence of the user's overrides lives in [Store]; this module only
 * defines the bands, works out a station's ITU region, and resolves which bands are
 * visible by default and after overrides.
 */

/** ITU Radio Regulations regions (RR Article 5). */
enum class ItuRegion(val number: Int, val description: String) {
    R1(1, "Europe, Africa, the Middle East west of the Persian Gulf, the former USSR and Mongolia"),
    R2(2, "The Americas, Greenland and the eastern Pacific"),
    R3(3, "Asia east of the Persian Gulf, Australasia and the western Pacific");

    companion object {
        fun of(number: Int): ItuRegion? = entries.firstOrNull { it.number == number }
    }
}

/**
 * One amateur band relevant to tropospheric ducting.
 *
 * @property ituRegions regions where amateurs have an allocation. For bands with
 *   [nationalOnly] = true there is no ITU-wide amateur allocation; the region set lists
 *   where national allocations are common, and availability varies by country.
 * @property allocationNote short human-readable summary of the allocation.
 */
data class TropoBand(
    val id: String,
    val label: String,
    val frequencyMHz: Double,
    val isMicrowave: Boolean,
    val ituRegions: Set<ItuRegion>,
    val nationalOnly: Boolean = false,
    val allocationNote: String = "",
) {
    /** Free-space wavelength in metres. */
    val wavelengthM: Double get() = TropoBands.SPEED_OF_LIGHT_MHZ_M / frequencyMHz

    /**
     * The thinnest duct (in metres) that can still trap this band, from the waveguide
     * cut-off for an atmospheric duct: λmax ≈ 0.014 · d^1.5 (λ and d in metres), so
     * d_min = (λ / 0.014)^(2/3). About 28 m at 2m and 6.5 m at 23cm.
     */
    val minDuctThicknessM: Double get() = (wavelengthM / TropoBands.DUCT_CUTOFF_K).pow(2.0 / 3.0)

    /**
     * How readily the band is trapped by a duct, relative to 2m (= 1.0). Because
     * d_min ∝ f^(-2/3), the factor is (f / 144 MHz)^(2/3): higher bands are held by
     * shallower ducts (70cm ≈ 2.1, 23cm ≈ 4.3), lower VHF needs deep ducts (6m ≈ 0.49).
     * This models trapping only; path loss, absorption and rain fade at microwave
     * frequencies are separate effects.
     */
    val ductSensitivityFactor: Double
        get() = TropoBands.REFERENCE_DUCT_THICKNESS_M / minDuctThicknessM
}

object TropoBands {
    const val SPEED_OF_LIGHT_MHZ_M = 299.792458   // c in MHz·m
    /** Waveguide cut-off constant for atmospheric ducts: λmax = 0.014 · d^1.5 (metres). */
    const val DUCT_CUTOFF_K = 0.014
    /** 2m is the reference band for [TropoBand.ductSensitivityFactor]. */
    const val REFERENCE_MHZ = 144.0
    val REFERENCE_DUCT_THICKNESS_M = (SPEED_OF_LIGHT_MHZ_M / REFERENCE_MHZ / DUCT_CUTOFF_K).pow(2.0 / 3.0)

    private val ALL_REGIONS = setOf(ItuRegion.R1, ItuRegion.R2, ItuRegion.R3)

    /** Core VHF/UHF bands, shown by default where allocated. */
    val CORE: List<TropoBand> = listOf(
        TropoBand("6m", "6m", 50.0, false, ALL_REGIONS,
            allocationNote = "50–54 MHz in Regions 2 and 3; 50–52 MHz in Region 1 (WRC-19)"),
        TropoBand("4m", "4m", 70.0, false, setOf(ItuRegion.R1), nationalOnly = true,
            allocationNote = "No ITU allocation; national 70 MHz allocations in many Region 1 " +
                "countries (e.g. UK, Ireland, South Africa) and a few elsewhere"),
        TropoBand("2m", "2m", 144.0, false, ALL_REGIONS,
            allocationNote = "144–146 MHz in Region 1; 144–148 MHz in Regions 2 and 3"),
        TropoBand("1.25m", "1.25m", 222.0, false, setOf(ItuRegion.R2),
            allocationNote = "222–225 MHz in Region 2 (e.g. USA, Canada) only"),
        TropoBand("70cm", "70cm", 432.0, false, ALL_REGIONS,
            allocationNote = "430–440 MHz (420–450 MHz in parts of Region 2), secondary"),
        TropoBand("23cm", "23cm", 1296.0, false, ALL_REGIONS,
            allocationNote = "1240–1300 MHz, secondary"),
    )

    /** Microwave bands: an optional group, collapsed and hidden by default. */
    val MICROWAVE: List<TropoBand> = listOf(
        TropoBand("13cm", "13cm", 2320.0, true, ALL_REGIONS,
            allocationNote = "2300–2450 MHz, secondary; sub-bands vary by country"),
        TropoBand("9cm", "9cm", 3400.0, true, ALL_REGIONS,
            allocationNote = "3300–3500 MHz in Regions 2 and 3; 3400–3410 MHz in parts of Region 1"),
        TropoBand("6cm", "6cm", 5760.0, true, ALL_REGIONS,
            allocationNote = "5650–5850 MHz, secondary"),
        TropoBand("3cm", "3cm", 10368.0, true, ALL_REGIONS,
            allocationNote = "10.0–10.5 GHz, secondary"),
    )

    val ALL: List<TropoBand> = CORE + MICROWAVE

    fun byId(id: String): TropoBand? = ALL.firstOrNull { it.id == id }

    /**
     * Default visible bands for a station: the core bands allocated in its ITU region
     * (including national-only allocations listed for that region), in frequency order.
     * Microwave bands are opt-in. If the grid can't be read, all core bands are shown.
     */
    fun getDefaultBandsForGrid(maidenheadGrid: String): List<TropoBand> {
        val region = ituRegionForGrid(maidenheadGrid) ?: return CORE
        return defaultBandsForRegion(region)
    }

    fun defaultBandsForRegion(region: ItuRegion): List<TropoBand> = CORE.filter { region in it.ituRegions }

    /**
     * Bands to show after the user's manual choices: [overrides] maps band id to
     * visible (true) or hidden (false) and wins over the regional default for that
     * band; bands without an override keep their default. Any band, including ones not
     * allocated in the user's region, can be switched on. Unknown ids are ignored.
     */
    fun resolveVisibleBands(maidenheadGrid: String, overrides: Map<String, Boolean>): List<TropoBand> {
        val defaults = getDefaultBandsForGrid(maidenheadGrid).map { it.id }.toSet()
        return ALL.filter { overrides[it.id] ?: (it.id in defaults) }
    }

    // ---------------------------------------------------------------- ITU region

    /**
     * ITU region for a Maidenhead locator (2, 4, 6 or 8 characters; the centre of the
     * square is used). Returns null if the locator can't be read.
     *
     * Follows the region boundary lines A, B and C of RR Article 5, plus the rule that
     * the former-USSR states and Mongolia east of line A belong to Region 1. Those
     * national borders are approximated with simple latitude/longitude limits, so a
     * locator right on a border may resolve to the neighbouring region; that's what
     * the per-band overrides are for.
     */
    fun ituRegionForGrid(maidenheadGrid: String): ItuRegion? {
        val (lat, lon) = gridCentre(maidenheadGrid) ?: return null
        return ituRegionFor(lat, lon)
    }

    fun ituRegionFor(lat: Double, lon: Double): ItuRegion {
        val east = ((lon % 360) + 360) % 360           // 0..360 east of Greenwich
        val signed = if (east >= 180) east - 360 else east // -180..180
        val b = lineB(lat)   // degrees, -180..180 (Atlantic)
        val a = lineA(lat)   // degrees east (Asia)
        if (signed >= b && signed < a) return ItuRegion.R1
        val c = lineC(lat)   // degrees east, 0..360 (Pacific)
        if (east >= a && east < c) {
            return if (formerUssrOrMongolia(lat, east, c)) ItuRegion.R1 else ItuRegion.R3
        }
        return ItuRegion.R2
    }

    /** Centre of a Maidenhead square, as (lat, lon). Accepts 2 to 8 characters. */
    fun gridCentre(grid: String): Pair<Double, Double>? {
        val g = grid.trim().uppercase()
        if (g.length < 2 || g.length % 2 != 0 || g.length > 8) return null
        var lon = -180.0; var lat = -90.0
        var lonSize = 20.0; var latSize = 10.0
        for (pair in 0 until g.length / 2) {
            val x = g[pair * 2]; val y = g[pair * 2 + 1]
            when (pair) {
                0 -> { if (x !in 'A'..'R' || y !in 'A'..'R') return null
                    lon += (x - 'A') * lonSize; lat += (y - 'A') * latSize }
                1 -> { if (!x.isDigit() || !y.isDigit()) return null
                    lonSize /= 10; latSize /= 10; lon += (x - '0') * lonSize; lat += (y - '0') * latSize }
                2 -> { if (x !in 'A'..'X' || y !in 'A'..'X') return null
                    lonSize /= 24; latSize /= 24; lon += (x - 'A') * lonSize; lat += (y - 'A') * latSize }
                3 -> { if (!x.isDigit() || !y.isDigit()) return null
                    lonSize /= 10; latSize /= 10; lon += (x - '0') * lonSize; lat += (y - '0') * latSize }
            }
        }
        return (lat + latSize / 2) to (lon + lonSize / 2)
    }

    /** Line B (Region 1 / Region 2, Atlantic): 10°W to 72°N, then 50°W 40°N, then 20°W 10°S, then 20°W. */
    private val LINE_B = GreatCirclePath(listOf(90.0 to -10.0, 72.0 to -10.0, 40.0 to -50.0, -10.0 to -20.0, -90.0 to -20.0))

    /** Line A (Region 1 / Region 3): 40°E to 40°N, then 60°E at the Tropic of Cancer, then 60°E. */
    private val LINE_A = GreatCirclePath(listOf(90.0 to 40.0, 40.0 to 40.0, 23.5 to 60.0, -90.0 to 60.0))

    /**
     * Line C (Region 2 / Regions 1 and 3, Pacific): Bering Strait at 65°30'N (168°58'W), then
     * 165°E 50°N, then 170°W 10°N, along 10°N to 120°W, then 120°W. In degrees east (0..360).
     */
    private val LINE_C = GreatCirclePath(listOf(90.0 to 191.03, 65.5 to 191.03, 50.0 to 165.0, 10.0 to 190.0,
        10.0 to 240.0, -90.0 to 240.0))

    private fun lineB(lat: Double) = LINE_B.lonAt(lat)
    private fun lineA(lat: Double) = LINE_A.lonAt(lat)
    /** Below 10°N line C runs along 120°W; at exactly 10°N it turns along the parallel. */
    private fun lineC(lat: Double) = if (lat < 10.0) 240.0 else LINE_C.lonAt(lat)

    /**
     * Region 1 also covers Armenia, Azerbaijan, Georgia, Kazakhstan, Kyrgyzstan, Mongolia,
     * the Russian Federation, Tajikistan, Turkmenistan, Türkiye, Ukraine and Uzbekistan,
     * and the area north of Russia between lines A and C. Approximated by latitude bands.
     */
    private fun formerUssrOrMongolia(lat: Double, east: Double, lineC: Double): Boolean = when {
        east < 44.8 -> lat >= 37.0              // eastern Türkiye, Armenia, Georgia
        east < 48.0 -> lat >= 38.9              // Azerbaijan (Iran lies south)
        east < 54.0 -> lat >= 40.0              // Caspian coast, Kazakhstan, Turkmenistan
        east < 61.0 -> lat >= 37.5              // Turkmenistan (Iran lies south)
        east < 73.0 -> lat >= 37.0              // Uzbekistan, Tajikistan (Afghanistan south)
        east < 80.0 -> lat >= 39.5              // Kyrgyzstan, Kazakhstan (China, Kashmir south)
        east < 87.5 -> lat >= 45.0              // eastern Kazakhstan (Xinjiang south)
        east < 96.0 -> lat >= 45.0              // western Mongolia, Altai
        east < 120.0 -> lat >= 42.5             // Mongolia, Russia (China south)
        east < 130.0 -> lat >= 49.0             // Russia along the Amur (China south)
        east < 139.5 -> lat >= 42.5             // Primorye (Korea, Japan south)
        else -> lat >= 46.0 && east < lineC     // Sakhalin, Kamchatka, Chukotka (Japan south)
    }

    /**
     * A boundary made of great-circle segments between (lat, lon) points ordered from north
     * to south. [lonAt] gives the boundary's longitude at a latitude, by sampling each
     * segment along the great circle (the RR lines are great-circle arcs, which bow
     * noticeably away from straight lines on a lat/lon grid at high latitudes).
     */
    private class GreatCirclePath(points: List<Pair<Double, Double>>) {
        private val lats: DoubleArray
        private val lons: DoubleArray

        init {
            val la = ArrayList<Double>(); val lo = ArrayList<Double>()
            for (i in 0 until points.size - 1) {
                val (lat1, lon1) = points[i]; val (lat2, lon2) = points[i + 1]
                val steps = 200
                for (s in 0 until steps) {
                    val (lat, lon) = if (lat1 == lat2 || lon1 == lon2 || kotlin.math.abs(lat1) == 90.0 || kotlin.math.abs(lat2) == 90.0) {
                        // Meridians, parallels and segments from a pole: interpolate directly.
                        val f = s / steps.toDouble()
                        (lat1 + (lat2 - lat1) * f) to (lon1 + (lon2 - lon1) * f)
                    } else greatCirclePoint(lat1, lon1, lat2, lon2, s / steps.toDouble())
                    la += lat; lo += lon
                }
            }
            la += points.last().first; lo += points.last().second
            lats = la.toDoubleArray(); lons = lo.toDoubleArray()
        }

        fun lonAt(lat: Double): Double {
            val y = lat.coerceIn(-90.0, 90.0)
            for (i in 0 until lats.size - 1) {
                val a = lats[i]; val b = lats[i + 1]
                if (y <= a && y >= b) {
                    if (a == b) return lons[i]   // along a parallel: take where it starts
                    val f = (a - y) / (a - b)
                    return lons[i] + (lons[i + 1] - lons[i]) * f
                }
            }
            return lons.last()
        }

        /** Point a fraction [f] of the way along the great circle from point 1 to point 2. */
        private fun greatCirclePoint(lat1: Double, lon1: Double, lat2: Double, lon2: Double, f: Double): Pair<Double, Double> {
            val p1 = Math.toRadians(lat1); val l1 = Math.toRadians(lon1)
            val p2 = Math.toRadians(lat2); val l2 = Math.toRadians(lon2)
            val dPhi = p2 - p1; val dLam = l2 - l1
            val h = sin(dPhi / 2).pow(2) + cos(p1) * cos(p2) * sin(dLam / 2).pow(2)
            val delta = 2 * asin(sqrt(h))
            if (delta == 0.0) return lat1 to lon1
            val a = sin((1 - f) * delta) / sin(delta)
            val b = sin(f * delta) / sin(delta)
            val x = a * cos(p1) * cos(l1) + b * cos(p2) * cos(l2)
            val y = a * cos(p1) * sin(l1) + b * cos(p2) * sin(l2)
            val z = a * sin(p1) + b * sin(p2)
            val lat = Math.toDegrees(atan2(z, sqrt(x * x + y * y)))
            var lon = Math.toDegrees(atan2(y, x))
            // Keep longitudes continuous with the endpoints (line C runs past 180°).
            while (lon < minOf(lon1, lon2) - 180) lon += 360
            while (lon > maxOf(lon1, lon2) + 180) lon -= 360
            return lat to lon
        }
    }
}
