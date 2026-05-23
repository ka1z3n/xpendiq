package com.example.spendy.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object SmsPermissions {

    /** All runtime permissions Spendy asks for. POST_NOTIFICATIONS only on API 33+. */
    val required: Array<String> = buildList {
        add(Manifest.permission.RECEIVE_SMS)
        add(Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    /** True when both SMS perms are granted (the two we actually need to ingest). */
    fun smsGranted(context: Context): Boolean {
        return granted(context, Manifest.permission.RECEIVE_SMS) &&
            granted(context, Manifest.permission.READ_SMS)
    }

    /** True when we should show the rationale UI before re-requesting. */
    fun shouldShowRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.READ_SMS,
        )
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
