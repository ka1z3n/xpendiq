package com.kaizenll.xpendiq.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kaizenll.xpendiq.MainActivity
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.entitlement.EntitlementState
import com.kaizenll.xpendiq.util.CurrencyFormat

/**
 * Posts the "trial ending" reminder — but only if the user is still mid-trial and unsubscribed when
 * it actually runs (state can change between scheduling and firing). Tapping it opens the paywall.
 */
class TrialReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? XpendiqApplication ?: return Result.success()
        app.entitlement.refresh()
        val state = app.entitlement.state.value
        if (state !is EntitlementState.InTrial) return Result.success() // expired/subscribed → drop

        val spent = CurrencyFormat.paiseToInr(app.repository.visibleSpendInrPaise())
        val title = applicationContext.resources.getQuantityString(
            R.plurals.trial_reminder_title, state.daysLeft, state.daysLeft,
        )
        val text = applicationContext.getString(R.string.trial_reminder_text, spent)

        TrialReminder.ensureChannel(applicationContext)
        val tapIntent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_PAYWALL, true)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, TrialReminder.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        // No-op on Android 13+ when POST_NOTIFICATIONS isn't granted; never crash the worker.
        if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            try {
                NotificationManagerCompat.from(applicationContext)
                    .notify(TrialReminder.NOTIF_ID, notification)
            } catch (e: SecurityException) {
                // Permission revoked between the check and notify — ignore.
            }
        }
        return Result.success()
    }
}
