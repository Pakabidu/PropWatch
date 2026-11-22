package uk.dx.propwatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CH_ALERT = "alerts"
    private const val CH_HEADSUP = "headsup"
    private const val REPEAT_AFTER_MS = 12 * 3600_000L

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Very good conditions",
            NotificationManager.IMPORTANCE_HIGH).apply { description = "Tropo, aurora and Es openings" })
        nm.createNotificationChannel(NotificationChannel(CH_HEADSUP, "Heads-ups",
            NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Meteor shower peaks" })
    }

    /** Notify for each mode that has just started meeting its threshold (or is still on after 12 h). */
    fun handle(ctx: Context, r: Report) {
        ensureChannels(ctx)
        for ((i, f) in r.findings.withIndex()) {
            if (!f.alert) {
                Store.markActive(ctx, f.mode, false)
                continue
            }
            val fresh = !Store.wasActive(ctx, f.mode)
            val stale = r.timeMs - Store.lastNotified(ctx, f.mode) > REPEAT_AFTER_MS
            if (fresh || stale) {
                post(ctx, 100 + i, f)
                Store.markActive(ctx, f.mode, true, r.timeMs)
            } else {
                Store.markActive(ctx, f.mode, true)
            }
        }
    }

    fun post(ctx: Context, id: Int, f: Finding) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, if (f.lowGrade) CH_HEADSUP else CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(f.headline)
            .setContentText(f.detail.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(f.detail))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(if (f.lowGrade) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}
