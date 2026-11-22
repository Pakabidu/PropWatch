package uk.dx.propwatch

/**
 * Where to look for tropospheric ducts around a station, anywhere in the world, and
 * how to read the weather model's answer. Pure Kotlin (no Android) so it can be
 * unit-tested on a plain JVM; [Checker] fetches the model profiles.
 *
 * The station is surrounded by two rings of points, 250 km and 500 km out on the
 * eight compass bearings. A duct is only useful if it carries a signal away from the
 * station, so a bearing counts as a ducting path in a given hour when the duct is
 * continuous along it: over the station and the 250 km point, or over both the 250 km
 * and 500 km points.
 */
object TropoPaths {
    /** -157 N/km or steeper is a duct; -79 to -157 is super-refraction. */
    const val DUCT_GRADIENT = -157.0
    val BEARINGS = listOf(0, 45, 90, 135, 180, 225, 270, 315)
    val RINGS_KM = listOf(250.0, 500.0)

    /** A forecast point: the station itself (bearing null) or a ring point. */
    data class Point(val lat: Double, val lon: Double, val bearing: Int?, val km: Double)

    /** The station first, then the 250 km ring, then the 500 km ring, each in [BEARINGS] order. */
    fun points(homeLat: Double, homeLon: Double): List<Point> =
        listOf(Point(homeLat, homeLon, null, 0.0)) + RINGS_KM.flatMap { km ->
            BEARINGS.map { b ->
                val (lat, lon) = Geo.destination(homeLat, homeLon, b.toDouble(), km)
                Point(lat, lon, b, km)
            }
        }

    /** Ducting along one bearing: in how many hours, and how far out the duct reached. */
    data class Direction(val bearing: Int, val hours: Int, val reachKm: Double)

    /** All forecast hours with a ducting path, and the bearings involved (most hours first). */
    data class Result(val ductHours: List<Long>, val directions: List<Direction>)

    /**
     * [grads] holds, for each point in [points] order, the steepest refractivity gradient
     * (N/km) by forecast hour. Hours missing for a point count as not ducting.
     */
    fun evaluate(grads: List<Map<Long, Double>>, threshold: Double = DUCT_GRADIENT): Result {
        val n = BEARINGS.size
        require(grads.size == 1 + n * RINGS_KM.size) { "expected ${1 + n * RINGS_KM.size} points, got ${grads.size}" }
        fun ducting(i: Int, t: Long) = (grads[i][t] ?: 0.0) <= threshold
        val hours = grads.flatMap { it.keys }.toSortedSet()
        val ductHours = mutableListOf<Long>()
        val count = IntArray(n)
        val reach = DoubleArray(n)
        for (t in hours) {
            var any = false
            for (k in 0 until n) {
                val home = ducting(0, t); val near = ducting(1 + k, t); val far = ducting(1 + n + k, t)
                val km = when {
                    near && far -> RINGS_KM[1]
                    home && near -> RINGS_KM[0]
                    else -> 0.0
                }
                if (km > 0) { any = true; count[k]++; reach[k] = maxOf(reach[k], km) }
            }
            if (any) ductHours += t
        }
        val dirs = (0 until n).filter { count[it] > 0 }
            .map { Direction(BEARINGS[it], count[it], reach[it]) }
            .sortedWith(compareByDescending<Direction> { it.hours }.thenByDescending { it.reachKm })
        return Result(ductHours, dirs)
    }
}
