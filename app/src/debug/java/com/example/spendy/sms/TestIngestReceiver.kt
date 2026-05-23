package com.example.spendy.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.spendy.SpendyApplication
import kotlinx.coroutines.launch

/**
 * Debug-only helper for simulating bank SMS via `adb shell am broadcast`.
 *
 * This class lives in `src/debug/`, so the manifest-merger only picks it up for debug builds.
 * Release builds never include it — no exported attack surface in production.
 *
 * Example:
 *
 * ```
 * adb shell am broadcast \
 *   -a com.example.spendy.TEST_INGEST \
 *   -n com.example.spendy/.sms.TestIngestReceiver \
 *   --es sender "VM-SBICRD-S" \
 *   --es body "Rs.23,325.00 spent on your SBI Credit Card ending 9999 at INDIGOAIRLINE on 23/05/26."
 * ```
 *
 * Add `--ez bypass true` to simulate the notification-listener path (bypasses the sender
 * shortcode check) — useful for testing RCS-style messages with a friendly sender like
 * "SBI Card".
 */
class TestIngestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val sender = intent.getStringExtra("sender") ?: run {
            Log.w(TAG, "missing 'sender' extra")
            return
        }
        val body = intent.getStringExtra("body") ?: run {
            Log.w(TAG, "missing 'body' extra")
            return
        }
        val bypass = intent.getBooleanExtra("bypass", false)
        val now = System.currentTimeMillis()

        val app = context.applicationContext as? SpendyApplication ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                val result = app.repository.ingestSms(
                    sender = sender,
                    body = body,
                    receivedAtMillis = now,
                    bypassSenderCheck = bypass,
                )
                Log.i(TAG, "ingest sender='$sender' bypass=$bypass -> $result")
            } catch (e: Exception) {
                Log.w(TAG, "ingest threw: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SpendyTestIngest"
        const val ACTION = "com.example.spendy.TEST_INGEST"
    }
}
