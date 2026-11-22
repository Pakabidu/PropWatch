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

    /** Per-mode bookkeeping so the same opening doesn't buzz the phone every few hours. */
    fun wasActive(ctx: Context, mode: String) = p(ctx).getBoolean("active_$mode", false)
    fun lastNotified(ctx: Context, mode: String) = p(ctx).getLong("notified_$mode", 0L)
    fun markActive(ctx: Context, mode: String, active: Boolean, notifiedAt: Long? = null) {
        val e = p(ctx).edit().putBoolean("active_$mode", active)
        if (notifiedAt != null) e.putLong("notified_$mode", notifiedAt)
        e.apply()
    }
}
