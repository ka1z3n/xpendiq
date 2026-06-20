package com.kaizenll.xpendiq.util

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.util.Locale

/**
 * Non-identifying diagnostics appended to an issue report so we can reproduce parsing/UI bugs.
 * App version, OS, device model, locale, and whether the capture permissions are on — no PII.
 */
object DeviceInfo {

    fun diagnostics(context: Context): String {
        val pkg = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = pkg?.versionName ?: "?"
        val versionCode = pkg?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
        val sms = if (SmsPermissions.smsGranted(context)) "granted" else "denied"
        val notif = if (NotificationAccess.isGranted(context)) "granted" else "denied"

        return buildString {
            appendLine("App: Xpendiq $versionName ($versionCode)")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Locale: ${Locale.getDefault()}")
            appendLine("SMS access: $sms")
            append("Notification access: $notif")
        }
    }
}
