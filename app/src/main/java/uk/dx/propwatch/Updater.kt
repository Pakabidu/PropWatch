package uk.dx.propwatch

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the GitHub "latest" release for a newer build, downloads it and hands it to the
 * system installer. The CI workflow publishes version.txt (its build number, which is also
 * the APK's versionCode) next to PropWatch.apk. Android never installs silently: the user
 * confirms the install, and the first time allows PropWatch to install apps.
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

    // ---------------------------------------------------------------- download and install

    private fun dir(ctx: Context) = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    private fun apk(ctx: Context, build: Long) = File(dir(ctx), "PropWatch-$build.apk")

    /** The downloaded APK for [build], if it's complete and still there. */
    fun downloaded(ctx: Context, build: Long): File? {
        val f = apk(ctx, build)
        if (!f.isFile || f.length() < 100_000) return null
        // A file that's still being written belongs to a running download.
        if (Store.updateBuild(ctx) == build && status(ctx) in setOf(DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED)) return null
        return f
    }

    /** Status of the last update download (a DownloadManager.STATUS_* value), or null if there isn't one. */
    fun status(ctx: Context): Int? {
        val id = Store.updateDownloadId(ctx).takeIf { it >= 0 } ?: return null
        val dm = ctx.getSystemService(DownloadManager::class.java) ?: return null
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
    }

    /** Is a download of [build] already under way? */
    fun downloading(ctx: Context, build: Long) = Store.updateBuild(ctx) == build &&
        status(ctx) in setOf(DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED)

    /**
     * Starts downloading [build] in the background with a progress notification, and
     * returns the download id. The file lands in the app's own folder, so no storage
     * permission is needed.
     */
    fun download(ctx: Context, build: Long): Long {
        val dm = ctx.getSystemService(DownloadManager::class.java) ?: throw IOException("No download manager")
        apk(ctx, build).delete()
        val req = DownloadManager.Request(Uri.parse(APK_URL))
            .setTitle("PropWatch update (build $build)")
            .setDescription("Downloading the new version")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, "PropWatch-$build.apk")
        val id = dm.enqueue(req)
        Store.saveUpdateDownload(ctx, id, build)
        return id
    }

    /** Can PropWatch hand packages to the installer, or does the user need to allow it first? */
    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** The settings page where the user allows PropWatch to install apps (needed once). */
    fun allowInstallsIntent(ctx: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))

    /** Opens the system installer on the downloaded [file]. */
    fun installIntent(ctx: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Deletes downloaded APKs for builds that are already installed (or older). */
    fun cleanUp(ctx: Context) {
        val current = currentBuild(ctx)
        dir(ctx)?.listFiles()?.forEach { f ->
            val b = Regex("PropWatch-(\\d+)\\.apk").matchEntire(f.name)?.groupValues?.get(1)?.toLongOrNull()
            if (b != null && b <= current) f.delete()
        }
    }
}
