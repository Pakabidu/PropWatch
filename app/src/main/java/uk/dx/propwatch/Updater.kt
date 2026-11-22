package uk.dx.propwatch

import android.content.Context
import android.os.Build
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the GitHub "latest" release for a newer build. The CI workflow publishes
 * version.txt (its build number, which is also the APK's versionCode) next to PropWatch.apk.
 */
object Updater {
    private const val BASE = "https://github.com/Pakabidu/PropWatch/releases/download/latest/"
    const val APK_URL = BASE + "PropWatch.apk"
    private const val VERSION_URL = BASE + "version.txt"

    data class Info(val current: Long, val latest: Long) {
        val newer get() = latest > current
    }

    fun currentBuild(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }

    fun currentName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** Blocking; call off the main thread. */
    fun check(ctx: Context): Info {
        val c = URL(VERSION_URL).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "PropWatch update check")
        try {
            val code = c.responseCode
            if (code == 404) throw IOException("No published build found yet")
            if (code !in 200..299) throw IOException("HTTP $code from GitHub")
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val latest = Regex("\\d+").find(text)?.value?.toLongOrNull()
                ?: throw IOException("Unexpected version file")
            return Info(currentBuild(ctx), latest)
        } finally {
            c.disconnect()
        }
    }
}
