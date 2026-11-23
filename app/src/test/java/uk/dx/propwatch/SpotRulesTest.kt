package uk.dx.propwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotRulesTest {
    // Station: Boston, Lincolnshire.
    private val homeLat = 52.98
    private val homeLon = -0.02
    private fun band(id: String) = TropoBands.byId(id)!!

    private var n = 0
    /** A path from near the station to (lat, lon), on [mhz]. Each call uses a new pair of callsigns. */
    private fun spot(lat: Double, lon: Double, mhz: Double, mode: String = "FT8",
                     fromLat: Double = homeLat, fromLon: Double = homeLon): Spot {
        n++
        return Spot("G$n", "DL$n", Geo.km(fromLat, fromLon, lat, lon), fromLat, fromLon, lat, lon, mode, (mhz * 1e6).toLong())
    }

    private fun kind(id: String, spots: List<Spot>, month: Int = 10, lat: Double = homeLat, lon: Double = homeLon) =
        SpotRules.evaluate(band(id), spots, lat, lon, month).kind

    // Places roughly 600, 800, 1000 and 1600 km from Boston.
    private val hamburg = 53.55 to 9.99          // ~670 km
    private val berlin = 52.52 to 13.40          // ~910 km
    private val copenhagen = 55.68 to 12.57      // ~860 km
    private val warsaw = 52.23 to 21.01          // ~1430 km
    private val madrid = 40.42 to -3.70          // ~1430 km
    private val rome = 41.90 to 12.50            // ~1600 km

    @Test fun windowsKeepOutSatellites() {
        assertTrue(SpotRules.inBand(spot(berlin.first, berlin.second, 2320.2), band("13cm")))
        assertTrue(SpotRules.inBand(spot(berlin.first, berlin.second, 2304.1), band("13cm")))
        assertFalse(SpotRules.inBand(spot(berlin.first, berlin.second, 2400.2), band("13cm")))    // QO-100 uplink
        assertTrue(SpotRules.inBand(spot(berlin.first, berlin.second, 10368.2), band("3cm")))
        assertFalse(SpotRules.inBand(spot(berlin.first, berlin.second, 10489.6), band("3cm")))   // QO-100 downlink
        assertFalse(SpotRules.inBand(spot(berlin.first, berlin.second, 145.5), band("70cm")))
    }

    @Test fun queryRangeFollowsChosenBands() {
        assertEquals(50_000_000L..1_297_000_000L, SpotRules.queryRangeHz(TropoBands.CORE))
        assertEquals(144_000_000L..433_000_000L, SpotRules.queryRangeHz(listOf(band("2m"), band("70cm"))))
        assertEquals(10_369_000_000L, SpotRules.queryRangeHz(TropoBands.CORE + band("3cm"))!!.last)
        assertEquals(null, SpotRules.queryRangeHz(emptyList()))
    }

    @Test fun twoMetresNeedsACluster() {
        val four = List(4) { spot(hamburg.first + it * 0.1, hamburg.second + 1, 144.174) }   // ~700-750 km
        assertEquals(SpotRules.Kind.QUIET, kind("2m", four))
        assertEquals(SpotRules.Kind.TROPO, kind("2m", four + spot(copenhagen.first, copenhagen.second, 144.174)))
        // Or two unusually long paths.
        val two = listOf(spot(warsaw.first, warsaw.second, 144.174), spot(madrid.first, madrid.second, 144.174))
        assertEquals(SpotRules.Kind.TROPO, kind("2m", two))
    }

    @Test fun twoMetreEsOnlyInSeason() {
        val es = listOf(spot(rome.first, rome.second, 144.174), spot(madrid.first, madrid.second, 144.174))
        assertEquals(SpotRules.Kind.ES, kind("2m", es, month = 6))
        assertEquals(SpotRules.Kind.TROPO, kind("2m", es, month = 10))   // out of season: long tropo paths
        // Southern hemisphere: the season is mirrored.
        assertTrue(SpotRules.esSeason(1, -34.0))     // southern summer
        assertTrue(SpotRules.esSeason(6, -34.0))     // southern winter peak
        assertFalse(SpotRules.esSeason(7, -34.0))
        assertFalse(SpotRules.esSeason(1, 52.0))     // northern winter
        assertTrue(SpotRules.esSeason(7, 52.0))
    }

    @Test fun meteorAndEmeModesIgnored() {
        val ms = List(6) { spot(berlin.first, berlin.second + it * 0.2, 144.36, mode = "MSK144") }
        assertEquals(SpotRules.Kind.QUIET, kind("2m", ms))
        assertEquals(0, SpotRules.evaluate(band("2m"), ms, homeLat, homeLon, 10).considered)
    }

    @Test fun onlyPathsTouchingTheStationCount() {
        // A busy opening around Tokyo means nothing to a station in England.
        val japan = List(6) { spot(43.0 + it * 0.1, 141.3, 144.174, fromLat = 35.7, fromLon = 139.7) }
        assertEquals(SpotRules.Kind.QUIET, kind("2m", japan))
        // ...but it does to a station in Tokyo.
        assertEquals(SpotRules.Kind.TROPO, kind("2m", japan, lat = 35.7, lon = 139.7))
    }

    @Test fun higherBandsNeedFewerPaths() {
        assertEquals(SpotRules.Kind.TROPO, kind("70cm", listOf(spot(berlin.first, berlin.second, 432.174),
            spot(copenhagen.first, copenhagen.second, 432.174))))
        assertEquals(SpotRules.Kind.QUIET, kind("23cm", listOf(spot(hamburg.first, hamburg.second, 1296.174))))   // ~670 km, could be aircraft scatter
        assertEquals(SpotRules.Kind.TROPO, kind("23cm", listOf(spot(berlin.first, berlin.second, 1296.174))))      // ~910 km
        assertEquals(SpotRules.Kind.TROPO, kind("3cm", listOf(spot(hamburg.first, hamburg.second, 10368.1))))
    }

    @Test fun sixMetres() {
        val es = listOf(spot(rome.first, rome.second, 50.313), spot(madrid.first, madrid.second, 50.313),
            spot(warsaw.first, warsaw.second, 50.313))
        assertEquals(SpotRules.Kind.ES, kind("6m", es, month = 12))     // 6m sees Es all year
        val dx = listOf(spot(40.7, -74.0, 50.313), spot(-23.5, -46.6, 50.313))   // New York, São Paulo
        assertEquals(SpotRules.Kind.DX, kind("6m", es + dx))
        val quiet = SpotRules.evaluate(band("6m"), es.take(2), homeLat, homeLon, 6)
        assertEquals(SpotRules.Kind.QUIET, quiet.kind)
        assertEquals(2, quiet.pairs)                                    // Es-range paths reported for context
    }

    @Test fun farEndIsAwayFromTheStation() {
        val s = spot(berlin.first, berlin.second, 144.174)
        assertEquals(berlin, SpotRules.farEnd(s, homeLat, homeLon))
        val reversed = s.copy(la = s.lb, loa = s.lob, lb = s.la, lob = s.loa)
        assertEquals(berlin, SpotRules.farEnd(reversed, homeLat, homeLon))
    }

    // ---- CB (27 MHz) ----

    private fun cb(spots: List<Spot>) = CbRules.evaluate(spots, homeLat, homeLon).kind

    @Test fun cbChannelsOnly() {
        assertTrue(CbRules.inCb(spot(madrid.first, madrid.second, 27.265)))
        assertTrue(CbRules.inCb(spot(madrid.first, madrid.second, 27.78125)))    // UK 27/81
        assertFalse(CbRules.inCb(spot(madrid.first, madrid.second, 28.074)))     // 10m FT8
    }

    @Test fun cbSkipNeedsTwoLongPaths() {
        assertEquals(CbRules.Kind.QUIET, cb(listOf(spot(madrid.first, madrid.second, 27.265))))
        assertEquals(CbRules.Kind.SKIP, cb(listOf(spot(madrid.first, madrid.second, 27.265), spot(rome.first, rome.second, 27.265))))
        // Ground wave across town doesn't count.
        assertEquals(CbRules.Kind.QUIET, cb(listOf(spot(53.2, 0.1, 27.265), spot(52.8, -0.3, 27.265))))
    }

    @Test fun cbDxAndFarAwayPaths() {
        val ny = 40.71 to -74.0
        val rio = -22.9 to -43.2
        assertEquals(CbRules.Kind.DX, cb(listOf(spot(ny.first, ny.second, 27.265), spot(rio.first, rio.second, 27.265))))
        // Skip between two places far from the station isn't ours.
        assertEquals(CbRules.Kind.QUIET, cb(listOf(
            spot(-33.9, 151.2, 27.265, fromLat = -37.8, fromLon = 145.0),
            spot(-27.5, 153.0, 27.265, fromLat = -37.8, fromLon = 145.0))))
    }
}
