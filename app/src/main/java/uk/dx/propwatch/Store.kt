package uk.dx.propwatch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Settings and the last report, kept in SharedPreferences. */
object Store {
    private const val PREFS = "propwatch"

    // Defaults: Boston, Lincolnshire (JO02aw).
    private const val DEF_LAT = 52.98
    private const val DEF_LON = -0.02

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun station(ctx: Context) = Station(
        p(ctx).getFloat("lat", DEF_LAT.toFloat()).toDouble(),
        p(ctx).getFloat("lon", DEF_LON.toFloat()).toDouble(),
    )

    fun saveStation(ctx: Context, s: Station) =
        p(ctx).edit().putFloat("lat", s.lat.toFloat()).putFloat("lon", s.lon.toFloat()).apply()

    /** Take the station position from the phone's location instead of typed coordinates. */
    fun useGps(ctx: Context) = p(ctx).getBoolean("use_gps", true)
    fun saveUseGps(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("use_gps", on).apply()

    /** Town and country for the station, reverse-geocoded; shown on the Settings location screen. */
    fun placeCity(ctx: Context): String? = p(ctx).getString("place_city", null)
    fun placeCountry(ctx: Context): String? = p(ctx).getString("place_country", null)
    fun savePlace(ctx: Context, city: String?, country: String?) =
        p(ctx).edit().putString("place_city", city).putString("place_country", country).apply()

    /** Footnote for the last phone fix, e.g. "±12 m · GPS · Sun 14:32". */
    fun fixNote(ctx: Context): String? = p(ctx).getString("fix_note", null)
    fun saveFixNote(ctx: Context, note: String?) = p(ctx).edit().putString("fix_note", note).apply()

    /** Cabinet style: the 1940s portable (default) or the communications receiver. */
    fun skin(ctx: Context): Radio.Skin =
        runCatching { Radio.Skin.valueOf(p(ctx).getString("skin", null) ?: "PORTABLE") }.getOrDefault(Radio.Skin.PORTABLE)
    fun saveSkin(ctx: Context, skin: Radio.Skin) = p(ctx).edit().putString("skin", skin.name).apply()

    /** Selected waveband button: all, hf, 6m, 2m or 70cm. */
    fun band(ctx: Context): String = p(ctx).getString("band", "all") ?: "all"
    fun saveBand(ctx: Context, band: String) = p(ctx).edit().putString("band", band).apply()

    fun intervalHours(ctx: Context) = p(ctx).getInt("interval", 3)
    fun saveInterval(ctx: Context, h: Int) = p(ctx).edit().putInt("interval", h).apply()

    fun enabled(ctx: Context) = p(ctx).getBoolean("enabled", true)
    fun saveEnabled(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("enabled", on).apply()

    fun sounds(ctx: Context) = p(ctx).getBoolean("sounds", true)
    fun saveSounds(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("sounds", on).apply()

    fun saveReport(ctx: Context, r: Report) {
        val arr = JSONArray()
        for (f in r.findings) {
            arr.put(JSONObject().put("mode", f.mode).put("alert", f.alert).put("headline", f.headline)
                .put("detail", f.detail).put("low", f.lowGrade))
        }
        p(ctx).edit().putString("report", JSONObject().put("time", r.timeMs).put("findings", arr).toString()).apply()
        appendHistory(ctx, r)
    }

    fun loadReport(ctx: Context): Report? {
        val s = p(ctx).getString("report", null) ?: return null
        return try {
            val o = JSONObject(s)
            val a = o.getJSONArray("findings")
            Report(o.getLong("time"), (0 until a.length()).map {
                val f = a.getJSONObject(it)
                Finding(f.getString("mode"), f.getBoolean("alert"), f.getString("headline"),
                    f.getString("detail"), f.optBoolean("low"))
            })
        } catch (e: Exception) {
            null
        }
    }

    /** One entry per check for the lid's 24-hour log chart: time plus a level per band. */
    data class LogEntry(val timeMs: Long, val levels: Map<String, Int>)

    private const val HISTORY_MS = 26 * 3600_000L

    private fun appendHistory(ctx: Context, r: Report) {
        val arr = JSONArray()
        for (e in history(ctx)) if (r.timeMs - e.timeMs < HISTORY_MS && e.timeMs < r.timeMs) arr.put(entryJson(e))
        arr.put(entryJson(LogEntry(r.timeMs, Bands.ALL.associate { it.first to Bands.level(r.findings, it.first) })))
        p(ctx).edit().putString("history", arr.toString()).apply()
    }

    private fun entryJson(e: LogEntry) = JSONObject().put("t", e.timeMs).apply {
        for ((k, v) in e.levels) put(k, v)
    }

    fun history(ctx: Context): List<LogEntry> {
        val s = p(ctx).getString("history", null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                LogEntry(o.getLong("t"), Bands.ALL.associate { it.first to o.optInt(it.first, 0) })
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Per-mode bookkeeping so the same opening doesn't buzz the phone every few hours. */
    fun wasActive(ctx: Context, mode: String) = p(ctx).getBoolean("active_$mode", false)
    fun lastNotified(ctx: Context, mode: String) = p(ctx).getLong("notified_$mode", 0L)
    fun markActive(ctx: Context, mode: String, active: Boolean, notifiedAt: Long? = null) {
        val e = p(ctx).edit().putBoolean("active_$mode", active)
        if (notifiedAt != null) e.putLong("notified_$mode", notifiedAt)
        e.apply()
    }

    // ---------------------------------------------------------------- tropo bands

    /** The station's Maidenhead locator, for working out its ITU region. */
    fun stationGrid(ctx: Context): String = station(ctx).let { Geo.latLonToLoc(it.lat, it.lon) }

    /**
     * The user's manual band choices: band id to visible/hidden. Only bands the user has
     * changed are stored, so the rest keep following the regional default (and follow the
     * station if it moves to another ITU region).
     */
    fun tropoBandOverrides(ctx: Context): Map<String, Boolean> {
        val s = p(ctx).getString("tropo_bands", null) ?: return emptyMap()
        return try {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { o.getBoolean(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Sets a band's visibility; null forgets the choice so the band follows its default again. */
    fun setTropoBandOverride(ctx: Context, id: String, visible: Boolean?) {
        val m = tropoBandOverrides(ctx).toMutableMap()
        if (visible == null) m.remove(id) else m[id] = visible
        p(ctx).edit().putString("tropo_bands", JSONObject(m as Map<*, *>).toString()).apply()
    }

    fun clearTropoBandOverrides(ctx: Context) = p(ctx).edit().remove("tropo_bands").apply()

    /** Bands to show: the regional defaults for the station's locator, after the user's choices. */
    fun visibleTropoBands(ctx: Context): List<TropoBand> =
        TropoBands.resolveVisibleBands(stationGrid(ctx), tropoBandOverrides(ctx))
}
