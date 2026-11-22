package uk.dx.propwatch

/**
 * How live PSKReporter spots on each tropo band are judged. Pure Kotlin (no Android),
 * so the rules can be unit-tested on a plain JVM; [Checker] fetches the spots and
 * turns the [Verdict]s into readings.
 */

/** One reception report: callsigns a and b, path length, both ends, mode and frequency. */
data class Spot(val a: String, val b: String, val km: Double, val la: Double, val loa: Double,
                val lb: Double, val lob: Double, val mode: String, val hz: Long)

/**
 * Thresholds for one band. A path "touches" the station if either end is within
 * [SpotRules.NEAR_KM] of it. Null distances switch that test off for the band.
 *
 * - tropoKm, tropoPairs: a cluster of tropo-range paths (up to [SpotRules.TROPO_MAX_KM]).
 * - longKm, longPairs: or a few unusually long tropo paths.
 * - esMinKm to esMaxKm, esPairs: sporadic E, single-hop paths with an end within [SpotRules.ES_AREA_KM].
 * - esInSeasonOnly: only look for Es in the local Es season (2m); 6m and 4m see Es all year.
 * - dxKm, dxPairs: multi-hop Es / F2 / TEP (6m).
 */
data class LiveRule(
    val tropoKm: Double?, val tropoPairs: Int,
    val longKm: Double?, val longPairs: Int,
    val esMinKm: Double? = null, val esMaxKm: Double = 2500.0, val esPairs: Int = 2, val esInSeasonOnly: Boolean = false,
    val dxKm: Double? = null, val dxPairs: Int = 2,
)

object SpotRules {
    /** A path touches the station's area when one end is this close to the station. */
    const val NEAR_KM = 800.0
    /** Es paths count when one end is within this distance (the Es "neighbourhood"). */
    const val ES_AREA_KM = 1500.0
    /** Longer "tropo" paths are more likely Es, F2 or EME; they don't count as tropo. */
    const val TROPO_MAX_KM = 2800.0

    /** Meteor-scatter and EME modes: not tropo, so they're ignored. */
    val NON_TROPO_MODES = setOf("MSK144", "FSK441", "Q65", "JT65", "JT4", "JT6M", "ISCAT")

    /**
     * Thresholds per band. On 2m a single 700 km spot isn't enough, because aircraft
     * scatter on FT8 regularly reaches 700–800 km; 23cm and up see aircraft scatter
     * too, so one path needs to be long before it counts. Activity is thin above
     * 70cm, so fewer paths are needed there.
     */
    fun rule(band: TropoBand): LiveRule = when (band.id) {
        "6m" -> LiveRule(tropoKm = null, tropoPairs = 0, longKm = null, longPairs = 0,
            esMinKm = 700.0, esPairs = 3, dxKm = 3500.0, dxPairs = 2)
        "4m" -> LiveRule(tropoKm = 500.0, tropoPairs = 2, longKm = 800.0, longPairs = 1,
            esMinKm = 900.0, esPairs = 2)
        "2m" -> LiveRule(tropoKm = 700.0, tropoPairs = 5, longKm = 1000.0, longPairs = 2,
            esMinKm = 1000.0, esPairs = 2, esInSeasonOnly = true)
        "1.25m" -> LiveRule(tropoKm = 600.0, tropoPairs = 2, longKm = 900.0, longPairs = 1)
        "70cm" -> LiveRule(tropoKm = 700.0, tropoPairs = 2, longKm = 1000.0, longPairs = 1)
        "23cm" -> LiveRule(tropoKm = 600.0, tropoPairs = 2, longKm = 900.0, longPairs = 1)
        else -> LiveRule(tropoKm = 400.0, tropoPairs = 1, longKm = 700.0, longPairs = 1)   // microwaves
    }

    /**
     * Where tropo shows up on PSKReporter: each band's terrestrial weak-signal segment,
     * in Hz. This keeps out FM and repeaters and, on 13cm and 3cm, the QO-100
     * geostationary satellite (2400 MHz uplink, 10489 MHz downlink), whose reports
     * would otherwise swamp those bands.
     */
    fun windowsHz(band: TropoBand): List<LongRange> = when (band.id) {
        "6m" -> listOf(mhz(50.0, 50.5))
        "4m" -> listOf(mhz(70.0, 70.5))
        "2m" -> listOf(mhz(144.0, 146.0))
        "1.25m" -> listOf(mhz(222.0, 222.25))
        "70cm" -> listOf(mhz(432.0, 433.0))
        "23cm" -> listOf(mhz(1296.0, 1297.0))
        "13cm" -> listOf(mhz(2304.0, 2305.0), mhz(2320.0, 2321.0))   // Region 2 and Region 1 calling
        "9cm" -> listOf(mhz(3400.0, 3401.0), mhz(3456.0, 3457.0))
        "6cm" -> listOf(mhz(5760.0, 5761.0))
        "3cm" -> listOf(mhz(10368.0, 10369.0))
        else -> listOf(mhz(band.frequencyMHz - 0.5, band.frequencyMHz + 0.5))
    }

    private fun mhz(lo: Double, hi: Double) = (lo * 1e6).toLong()..(hi * 1e6).toLong()

    /** The single PSKReporter query range (Hz) that covers every window of these bands. */
    fun queryRangeHz(bands: List<TropoBand>): LongRange? {
        val w = bands.flatMap { windowsHz(it) }
        if (w.isEmpty()) return null
        return w.minOf { it.first }..w.maxOf { it.last }
    }

    fun inBand(s: Spot, band: TropoBand) = windowsHz(band).any { s.hz in it }

    /** Is it Es season here? Northern summer plus the small December peak; mirrored in the south. */
    fun esSeason(month: Int, homeLat: Double) =
        if (homeLat >= 0) month in 5..8 || month == 12 else month in listOf(11, 12, 1, 2, 6)

    enum class Kind { QUIET, TROPO, ES, DX }

    /** Outcome for one band: what's happening, the paths behind it and the furthest one. */
    data class Verdict(val kind: Kind, val pairs: Int, val best: Spot?, val spots: List<Spot>, val considered: Int)

    fun pairKey(s: Spot) = listOf(s.a, s.b).sorted().joinToString("–")

    /** The end of a path that's further from the station: where to point the beam. */
    fun farEnd(s: Spot, homeLat: Double, homeLon: Double): Pair<Double, Double> =
        if (Geo.km(homeLat, homeLon, s.la, s.loa) >= Geo.km(homeLat, homeLon, s.lb, s.lob)) s.la to s.loa else s.lb to s.lob

    fun evaluate(band: TropoBand, allSpots: List<Spot>, homeLat: Double, homeLon: Double, month: Int): Verdict {
        val spots = allSpots.filter { inBand(it, band) && it.mode !in NON_TROPO_MODES }
        val r = rule(band)
        fun near(s: Spot, km: Double) =
            Geo.km(homeLat, homeLon, s.la, s.loa) <= km || Geo.km(homeLat, homeLon, s.lb, s.lob) <= km

        // Multi-hop DX first (6m): the rarest and most worth hearing about.
        if (r.dxKm != null) {
            val dx = spots.filter { it.km >= r.dxKm && near(it, NEAR_KM) }
            val pairs = dx.map(::pairKey).toSet().size
            if (pairs >= r.dxPairs) return Verdict(Kind.DX, pairs, dx.maxByOrNull { it.km }, dx, spots.size)
        }
        // Sporadic E: in season a cluster of single-hop paths is far more likely Es than tropo.
        if (r.esMinKm != null && (!r.esInSeasonOnly || esSeason(month, homeLat))) {
            val es = spots.filter { it.km in r.esMinKm..r.esMaxKm && near(it, ES_AREA_KM) }
            val pairs = es.map(::pairKey).toSet().size
            if (pairs >= r.esPairs) return Verdict(Kind.ES, pairs, es.maxByOrNull { it.km }, es, spots.size)
        }
        if (r.tropoKm != null) {
            val tropo = spots.filter { it.km >= r.tropoKm && it.km <= TROPO_MAX_KM && near(it, NEAR_KM) }
            val pairs = tropo.map(::pairKey).toSet().size
            val longPairs = if (r.longKm == null) 0 else tropo.filter { it.km >= r.longKm }.map(::pairKey).toSet().size
            if (pairs >= r.tropoPairs || (r.longKm != null && longPairs >= r.longPairs)) {
                return Verdict(Kind.TROPO, pairs, tropo.maxByOrNull { it.km }, tropo, spots.size)
            }
            return Verdict(Kind.QUIET, pairs, tropo.maxByOrNull { it.km }, tropo, spots.size)
        }
        // No tropo test (6m): report the Es-range paths seen, for context.
        val es = if (r.esMinKm == null) emptyList() else spots.filter { it.km in r.esMinKm..r.esMaxKm && near(it, ES_AREA_KM) }
        return Verdict(Kind.QUIET, es.map(::pairKey).toSet().size, es.maxByOrNull { it.km }, es, spots.size)
    }
}
