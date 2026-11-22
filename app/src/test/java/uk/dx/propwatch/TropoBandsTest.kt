package uk.dx.propwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TropoBandsTest {

    // ---------------------------------------------------------------- band table

    @Test fun noHfBands() {
        assertTrue(TropoBands.ALL.all { it.frequencyMHz >= 50.0 })
    }

    @Test fun coreAndMicrowaveGroups() {
        assertEquals(listOf("6m", "4m", "2m", "1.25m", "70cm", "23cm"), TropoBands.CORE.map { it.id })
        assertEquals(listOf("13cm", "9cm", "6cm", "3cm"), TropoBands.MICROWAVE.map { it.id })
        assertTrue(TropoBands.CORE.none { it.isMicrowave })
        assertTrue(TropoBands.MICROWAVE.all { it.isMicrowave })
        assertEquals(TropoBands.ALL.size, TropoBands.ALL.map { it.id }.toSet().size)
    }

    @Test fun allocations() {
        val b = { id: String -> TropoBands.byId(id)!! }
        assertEquals(setOf(ItuRegion.R2), b("1.25m").ituRegions)
        assertEquals(setOf(ItuRegion.R1), b("4m").ituRegions)
        assertTrue(b("4m").nationalOnly)
        for (id in listOf("6m", "2m", "70cm", "23cm", "13cm", "9cm", "6cm", "3cm")) {
            assertEquals(id, ItuRegion.entries.toSet(), b(id).ituRegions)
        }
    }

    @Test fun ductSensitivityRisesWithFrequency() {
        val f = TropoBands.ALL.map { it.ductSensitivityFactor }
        assertEquals(f.sorted(), f)                      // ALL is in frequency order
        val b = { id: String -> TropoBands.byId(id)!! }
        assertEquals(1.0, b("2m").ductSensitivityFactor, 1e-9)
        assertEquals(0.49, b("6m").ductSensitivityFactor, 0.01)
        assertEquals(2.08, b("70cm").ductSensitivityFactor, 0.01)
        assertEquals(4.33, b("23cm").ductSensitivityFactor, 0.01)
        assertEquals(28.0, b("2m").minDuctThicknessM, 0.5)   // λmax = 0.014·d^1.5
        assertEquals(6.5, b("23cm").minDuctThicknessM, 0.2)
    }

    // ---------------------------------------------------------------- ITU regions

    private fun region(grid: String) = TropoBands.ituRegionForGrid(grid)?.number

    @Test fun region1() {
        for ((place, grid) in listOf("London" to "IO91", "Moscow" to "KO85", "Reykjavik" to "HP94",
            "Azores" to "HM77", "Cape Verde" to "HK84", "Cape Town" to "JF96", "Johannesburg" to "KG43",
            "Riyadh" to "LL34", "Tashkent" to "MN41", "Almaty" to "MN83", "Ulaanbaatar" to "ON37",
            "Vladivostok" to "PN53", "Kamchatka" to "QO93")) assertEquals(place, 1, region(grid))
    }

    @Test fun region2() {
        for ((place, grid) in listOf("New York" to "FN30", "Nuuk" to "GP44", "Honolulu" to "BL11",
            "Anchorage" to "BP51", "Attu (Aleutians)" to "RO62", "Galapagos" to "EI49", "Rio" to "GG87",
            "Recife" to "HI21", "Buenos Aires" to "GF05")) assertEquals(place, 2, region(grid))
    }

    @Test fun region3() {
        for ((place, grid) in listOf("Tokyo" to "PM95", "Sydney" to "QF56", "Auckland" to "RF73",
            "Papeete" to "BH52", "Beijing" to "OM89", "Delhi" to "ML88", "Kabul" to "MM44",
            "Singapore" to "OJ11")) assertEquals(place, 3, region(grid))
    }

    @Test fun gridFormats() {
        assertEquals(1, region("IO"))          // field only
        assertEquals(1, region("io92xx"))      // lower case, 6 characters
        assertEquals(1, region("IO92XX45"))    // 8 characters
        assertNull(region(""))
        assertNull(region("I"))
        assertNull(region("IO9"))
        assertNull(region("ZZ00"))
        assertNull(region("IOAB"))
    }

    // ---------------------------------------------------------------- defaults and overrides

    private fun ids(list: List<TropoBand>) = list.map { it.id }

    @Test fun defaultsPerRegion() {
        assertEquals(listOf("6m", "4m", "2m", "70cm", "23cm"), ids(TropoBands.getDefaultBandsForGrid("IO92")))
        assertEquals(listOf("6m", "2m", "1.25m", "70cm", "23cm"), ids(TropoBands.getDefaultBandsForGrid("FN30")))
        assertEquals(listOf("6m", "2m", "70cm", "23cm"), ids(TropoBands.getDefaultBandsForGrid("QF56")))
        // Microwaves are opt-in everywhere.
        assertTrue(TropoBands.getDefaultBandsForGrid("FN30").none { it.isMicrowave })
        // An unreadable grid falls back to every core band.
        assertEquals(ids(TropoBands.CORE), ids(TropoBands.getDefaultBandsForGrid("??")))
    }

    @Test fun overridesWin() {
        val uk = "IO92"
        val v = TropoBands.resolveVisibleBands(uk, mapOf("4m" to false, "10ghz" to true, "3cm" to true, "1.25m" to true))
        assertFalse("4m" in ids(v))           // default on, switched off
        assertTrue("3cm" in ids(v))           // microwave opted in
        assertTrue("1.25m" in ids(v))         // allowed even though not allocated in Region 1
        assertEquals(listOf("6m", "2m", "1.25m", "70cm", "23cm", "3cm"), ids(v))   // unknown id ignored, frequency order
        assertEquals(ids(TropoBands.getDefaultBandsForGrid(uk)), ids(TropoBands.resolveVisibleBands(uk, emptyMap())))
    }
}
