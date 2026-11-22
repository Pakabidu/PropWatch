package uk.dx.propwatch

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Phone location via the platform LocationManager (no Google Play services needed),
 * plus reverse geocoding to a city and country.
 */
object Locator {

    data class Place(val city: String?, val country: String?)

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_GRANTED

    fun enabled(ctx: Context): Boolean {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun providers(lm: LocationManager) =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

    /** Newest cached fix from any provider, or null. Cheap; doesn't switch the GPS on. */
    @SuppressLint("MissingPermission")
    fun lastKnown(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        return providers(lm).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /**
     * Fresh fix: GPS first, network as a fallback. Calls back on the main thread exactly once,
     * with null if nothing arrives within [timeoutMs] and there's no cached fix either.
     */
    @SuppressLint("MissingPermission")
    fun current(ctx: Context, timeoutMs: Long = 25_000, done: (Location?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (lm == null || !hasPermission(ctx)) { main.post { done(null) }; return }
        val live = providers(lm).filter { it != LocationManager.PASSIVE_PROVIDER }
        if (live.isEmpty()) { main.post { done(lastKnown(ctx)) }; return }

        var finished = false
        val listeners = mutableListOf<LocationListener>()
        fun finish(loc: Location?) {
            if (finished) return
            finished = true
            listeners.forEach { runCatching { lm.removeUpdates(it) } }
            main.removeCallbacksAndMessages(null)
            done(loc ?: lastKnown(ctx))
        }
        var networkFix: Location? = null
        for (p in live) {
            val l = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    // Take GPS straight away; hold a network fix briefly in case GPS follows.
                    if (p == LocationManager.GPS_PROVIDER || loc.accuracy <= 100f) finish(loc)
                    else if (networkFix == null) {
                        networkFix = loc
                        main.postDelayed({ finish(networkFix) }, 6_000)
                    }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Required on API < 29")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            listeners += l
            runCatching { lm.requestLocationUpdates(p, 1000L, 0f, l, Looper.getMainLooper()) }
        }
        main.postDelayed({ finish(networkFix) }, timeoutMs)
    }

    /** City and country for a point. Blocking; call off the main thread. Never throws. */
    fun place(ctx: Context, lat: Double, lon: Double): Place {
        if (Geocoder.isPresent()) {
            try {
                @Suppress("DEPRECATION")
                val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                if (a != null) {
                    val city = a.locality ?: a.subAdminArea ?: a.adminArea
                    if (city != null || a.countryName != null) return Place(city, a.countryName)
                }
            } catch (_: Exception) {
                // Fall through to OpenStreetMap.
            }
        }
        return try {
            osm(lat, lon)
        } catch (_: Exception) {
            Place(null, null)
        }
    }

    /** OpenStreetMap Nominatim fallback for phones without a system geocoder. */
    private fun osm(lat: Double, lon: Double): Place {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10" +
            "&lat=%.5f&lon=%.5f&accept-language=%s".format(Locale.US, lat, lon, Locale.getDefault().language)
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "PropWatch/3 (Android amateur radio propagation app)")
        try {
            if (c.responseCode !in 200..299) return Place(null, null)
            val a = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONObject("address")
                ?: return Place(null, null)
            fun s(k: String) = a.optString(k).takeIf { it.isNotBlank() }
            return Place(s("city") ?: s("town") ?: s("village") ?: s("municipality") ?: s("county") ?: s("state"),
                s("country"))
        } finally {
            c.disconnect()
        }
    }
}
