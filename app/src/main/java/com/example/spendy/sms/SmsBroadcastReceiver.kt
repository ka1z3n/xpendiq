package com.example.spendy.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.spendy.SpendyApplication
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // SMS may arrive as multi-part; concatenate by sender.
        val sender = messages.first().originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val timestamp = messages.first().timestampMillis

        val app = context.applicationContext as? SpendyApplication ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.repository.ingestSms(sender, body, timestamp)
            } finally {
                pending.finish()
            }
        }
    }
}
