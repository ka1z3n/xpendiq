package com.kaizenll.xpendiq.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.util.Preferences
import java.util.concurrent.TimeUnit

/**
 * Schedules the one-shot "your trial is ending" nudge a few days before the 30-day trial lapses,
 * so users aren't surprised when the app goes read-only. Fires only if still in trial and
 * unsubscribed at the time (the worker re-checks).
 */
object TrialReminder {

    const val CHANNEL_ID = "trial_reminder"
    const val NOTIF_ID = 4201
    private const val UNIQUE_WORK = "xpendiq_trial_reminder"

    // Remind on day 25 of the 30-day trial → 5 days of runway.
    private const val REMIND_AFTER_DAYS = 25L

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.trial_reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /**
     * Enqueue the reminder for ~day 25, keeping any already-scheduled one. No-op once subscribed or
     * if day 25 is already past (near/After expiry there's nothing useful to remind).
     */
    fun schedule(context: Context) {
        if (Preferences.isSubscribed(context)) return
        val start = Preferences.getTrialStart(context).takeIf { it > 0L } ?: return
        val fireAt = start + TimeUnit.DAYS.toMillis(REMIND_AFTER_DAYS)
        val delay = fireAt - System.currentTimeMillis()
        if (delay <= 0L) return
        val req = OneTimeWorkRequestBuilder<TrialReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, req)
    }

    /** Debug helper: fire the reminder immediately, regardless of schedule. */
    fun fireNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<TrialReminderWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK + "_now", ExistingWorkPolicy.REPLACE, req)
    }
}
