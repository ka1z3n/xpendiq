package com.kaizenll.xpendiq.util

import android.content.Context
import androidx.core.content.edit

object Preferences {
    private const val PREFS = "xpendiq_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_APP_LOCK = "app_lock_enabled"

    fun isOnboardingComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingComplete(context: Context, value: Boolean = true) {
        prefs(context).edit { putBoolean(KEY_ONBOARDING_DONE, value) }
    }

    /** When on, the app requires device authentication (biometric or screen lock) to open. */
    fun isAppLockEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_APP_LOCK, false)

    fun setAppLockEnabled(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_APP_LOCK, value) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
