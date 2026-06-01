package com.kaizenll.xpendiq.sms

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kaizenll.xpendiq.XpendiqApplication
import kotlinx.coroutines.launch

/**
 * Catches transactional notifications from messaging apps (Google Messages, Samsung Messages).
 * Needed for RCS chatbot messages — banks like SBI Card now deliver "Rs.X spent..." via RCS,
 * which doesn't reach the SMS broadcast receiver or `content://sms/inbox`.
 *
 * The user must grant **Notification access** in system Settings; we never see the body until
 * that's granted.
 *
 * Dedup is via `Hashing.transactionHash(sender, body)`. If the same message arrives as both an
 * SMS (existing pipeline) and a Messages notification, the body content matches and only the
 * first one is stored.
 */
class MessageNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "listener connected — ready to capture notifications")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notif = sbn.notification
        val watched = sbn.packageName in WATCHED_PACKAGES
        // Log every posted notification so we can diagnose missing matches.
        Log.i(TAG, "posted pkg=${sbn.packageName} watched=$watched group=${sbn.isGroup} summary=${(notif?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY != 0}")
        if (!watched) return
        if (notif == null) return

        val title = notif.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val body = extractBody(notif).trim()
        Log.i(TAG, "  title='${title.take(60)}' body='${body.take(120)}'")

        if (title.isBlank() || body.isBlank()) {
            Log.w(TAG, "  skipped: blank title or body")
            return
        }
        if (sbn.isGroup && notif.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            Log.i(TAG, "  skipped: group summary")
            return
        }

        val app = applicationContext as? XpendiqApplication ?: return
        app.appScope.launch {
            try {
                val result = app.repository.ingestSms(
                    sender = title,
                    body = body,
                    receivedAtMillis = sbn.postTime,
                    bypassSenderCheck = true,
                )
                Log.i(TAG, "  ingest -> $result")
            } catch (e: Exception) {
                Log.w(TAG, "  ingest threw: ${e.message}")
            }
        }
    }

    /** Prefer expanded BigText / MessagingStyle messages; fall back to collapsed text. */
    private fun extractBody(notif: Notification): String {
        val extras = notif.extras ?: return ""

        // Newer Google Messages uses MessagingStyle and surfaces individual messages in EXTRA_MESSAGES.
        runCatching {
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notif)
            val latest = style?.messages?.lastOrNull()
            val text = latest?.text?.toString().orEmpty()
            if (text.isNotBlank()) return text
        }

        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        if (bigText.isNotBlank()) return bigText

        // text_lines (inbox style) — join the last line which is usually the most recent message.
        @Suppress("DEPRECATION")
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        textLines?.lastOrNull()?.toString()?.takeIf { it.isNotBlank() }?.let { return it }

        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    }

    companion object {
        private const val TAG = "XpendiqNotifListener"

        private val WATCHED_PACKAGES = setOf(
            "com.google.android.apps.messaging",   // Google Messages
            "com.samsung.android.messaging",        // Samsung Messages
            "com.android.mms",                      // AOSP / stock Messaging
        )
    }
}
