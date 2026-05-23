package com.example.spendy.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.spendy.sms.MessageNotificationListener

/**
 * Checks / requests the system "Notification access" permission needed by
 * [MessageNotificationListener]. There is no runtime-permission flow for this; the user must
 * toggle it in system Settings (Settings → Notifications → Notification access).
 */
object NotificationAccess {

    fun isGranted(context: Context): Boolean {
        val pkg = context.packageName
        val cls = MessageNotificationListener::class.java.name
        val target = ComponentName(pkg, cls)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabled.split(':').any { entry ->
            val cn = ComponentName.unflattenFromString(entry) ?: return@any false
            cn == target
        }
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
