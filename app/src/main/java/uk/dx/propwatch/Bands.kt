package uk.dx.propwatch

/** The wavebands PropWatch reports on, and which readings belong to each. */
object Bands {
    /** key, short label, plate label */
    val ALL = listOf(
        Triple("hf", "HF", "HF · 160–10 M"),
        Triple("6m", "6M", "6 METERS"),
        Triple("2m", "2M", "2 METERS"),
        Triple("70cm", "70", "70 CENTIMETERS"),
        Triple("cb", "CB", "CITIZENS BAND · 27 MC"),
    )

    fun of(mode: String): Set<String> = when (mode) {
        "HF bands" -> setOf("hf")
        "Live 6m spots" -> setOf("6m")
        "Live 2m spots" -> setOf("2m")
        "Live 70cm spots" -> setOf("70cm")
        Checker.CB_MODE -> setOf("cb")
        // Other tropo bands (4m, 1.25m, 23cm, microwaves) have no waveband button; they show under ALL.
        "Live 4m spots", "Live 1.25m spots", "Live 23cm spots", "Live 13cm spots", "Live 9cm spots",
        "Live 6cm spots", "Live 3cm spots" -> emptySet()
        "Tropo forecast" -> setOf("2m", "70cm")
        "Meteor scatter" -> setOf("6m", "2m")
        "Aurora" -> setOf("6m", "2m", "70cm")
        else -> setOf("hf", "6m", "2m", "70cm")
    }

    /** 0 quiet, 1 heads-up, 2 open; -1 if every reading for the band failed. */
    fun level(findings: List<Finding>, band: String?): Int {
        val f = findings.filter { band == null || band == "all" || band in of(it.mode) }
        return when {
            f.any { it.alert && !it.lowGrade } -> 2
            f.any { it.alert } -> 1
            f.isNotEmpty() && f.all { it.headline == "Couldn't check" } -> -1
            else -> 0
        }
    }
}
