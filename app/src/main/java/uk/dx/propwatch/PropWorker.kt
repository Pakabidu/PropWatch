package uk.dx.propwatch

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Background check run by WorkManager; survives reboots and app restarts. */
class PropWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val r = Checker(Store.station(applicationContext), Store.visibleTropoBands(applicationContext), Store.cb(applicationContext)).run()
        Store.saveReport(applicationContext, r)
        Notifier.handle(applicationContext, r)
        return Result.success()
    }

    companion object {
        private const val NAME = "propwatch-periodic"

        fun schedule(ctx: Context) {
            val wm = WorkManager.getInstance(ctx)
            if (!Store.enabled(ctx)) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val req = PeriodicWorkRequestBuilder<PropWorker>(Store.intervalHours(ctx).toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
