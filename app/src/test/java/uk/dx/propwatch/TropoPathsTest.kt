package uk.dx.propwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TropoPathsTest {
    private val home = 52.98 to -0.02
    private val n = TropoPaths.BEARINGS.size

    @Test fun pointsRingTheStation() {
        val pts = TropoPaths.points(home.first, home.second)
        assertEquals(1 + 2 * n, pts.size)
        assertEquals(home.first, pts[0].lat, 1e-9)
        for (p in pts.drop(1)) {
            assertEquals(p.km, Geo.km(home.first, home.second, p.lat, p.lon), 1.0)
            assertEquals(p.bearing!!.toDouble(), Geo.bearing(home.first, home.second, p.lat, p.lon), 0.5)
        }
        // Works across the date line and near the poles too.
        val fiji = TropoPaths.points(-17.7, 178.0)
        assertTrue(fiji.all { it.lon in -180.0..180.0 })
        assertEquals(250.0, Geo.km(-17.7, 178.0, fiji[1 + 2].lat, fiji[1 + 2].lon), 1.0)   // due east, past 180°
    }

    /** Gradients for every point: [ducting] indexes get -200 N/km for each hour in [hours], the rest -40. */
    private fun grads(hours: List<Long>, ducting: Map<Long, Set<Int>>): List<Map<Long, Double>> =
        (0 until 1 + 2 * n).map { i -> hours.associateWith { t -> if (i in (ducting[t] ?: emptySet())) -200.0 else -40.0 } }

    private fun near(bearing: Int) = 1 + TropoPaths.BEARINGS.indexOf(bearing)
    private fun far(bearing: Int) = 1 + n + TropoPaths.BEARINGS.indexOf(bearing)

    @Test fun noDuctNoPath() {
        val h = listOf(0L, 1L, 2L)
        val r = TropoPaths.evaluate(grads(h, emptyMap()))
        assertTrue(r.ductHours.isEmpty()); assertTrue(r.directions.isEmpty())
    }

    @Test fun isolatedDuctsDontMakeAPath() {
        val h = listOf(0L, 1L)
        // A duct over the station alone, or at the 500 km ring alone, carries nothing anywhere.
        val r = TropoPaths.evaluate(grads(h, mapOf(0L to setOf(0), 1L to setOf(far(90)))))
        assertTrue(r.ductHours.isEmpty())
    }

    @Test fun continuousDuctsMakePaths() {
        val h = listOf(0L, 1L, 2L)
        val r = TropoPaths.evaluate(grads(h, mapOf(
            0L to setOf(0, near(90)),                    // station + 250 km east
            1L to setOf(near(45), far(45)),              // 250 + 500 km north-east
            2L to setOf(near(45), far(45), 0, near(90)), // both
        )))
        assertEquals(h, r.ductHours)
        assertEquals(listOf(45, 90), r.directions.map { it.bearing })        // most hours first, then reach
        assertEquals(500.0, r.directions[0].reachKm, 0.0)
        assertEquals(250.0, r.directions[1].reachKm, 0.0)
        assertEquals(2, r.directions[0].hours)
    }
}
