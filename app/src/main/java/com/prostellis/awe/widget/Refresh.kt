package com.prostellis.awe.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.prostellis.awe.data.Store
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Background refreshes. One check runs every 15 minutes; each widget refreshes when its own
 * setting (30 or 60 minutes) says it's due. "Only when I tap refresh" widgets are skipped.
 */
object Refresh {
    private const val PERIODIC = "awe-periodic"
    private const val NOW = "awe-now"
    const val KEY_IDS = "ids"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    /** Downloads now and redraws the given widgets (all of them when [ids] is null, e.g. after a location change). */
    fun now(context: Context, ids: IntArray? = null) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(network)
            .setInputData(workDataOf(KEY_IDS to (ids ?: WidgetUpdater.allIds(context))))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Which widgets are due for an automatic refresh. */
    fun dueIds(context: Context, now: Long): IntArray {
        val store = Store(context)
        return WidgetUpdater.allIds(context).filter { id ->
            val minutes = store.widgetSettings(id).refresh.minutes ?: return@filter false
            now - store.widgetUpdatedAt(id) >= (minutes - 3) * 60_000L
        }.toIntArray()
    }
}

class RefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val now = System.currentTimeMillis()
        val ids = inputData.getIntArray(Refresh.KEY_IDS) ?: Refresh.dueIds(applicationContext, now)
        if (ids.isEmpty()) return Result.success()
        val store = Store(applicationContext)
        val ok = store.refreshForecast()
        ids.forEach { id ->
            if (ok) store.markWidgetUpdated(id, now)
            WidgetUpdater.update(applicationContext, id)
        }
        return if (ok || inputData.getIntArray(Refresh.KEY_IDS) == null) Result.success() else Result.retry()
    }
}

/** The refresh button on a widget: spinner on, download, redraw. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        WidgetUpdater.showSpinner(context, id)
        val pending = goAsync()
        val app = context.applicationContext
        thread {
            try {
                val store = Store(app)
                if (store.refreshForecast()) store.markWidgetUpdated(id, System.currentTimeMillis())
                WidgetUpdater.update(app, id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        fun pendingIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
            context, id,
            Intent(context, RefreshReceiver::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
