package com.kaizenll.xpendiq.util

import android.content.Context
import androidx.core.content.edit

object Preferences {
    private const val PREFS = "xpendiq_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_APP_LOCK = "app_lock_enabled"
    private const val KEY_USD_INR_RATE = "usd_inr_rate"

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

    /**
     * Manual USD→INR conversion rate (e.g. 83.0), or null when the user hasn't set one. Foreign
     * rows aren't folded into totals until a rate exists, keeping the app fully offline.
     */
    fun getUsdInrRate(context: Context): Double? =
        prefs(context).getFloat(KEY_USD_INR_RATE, 0f).takeIf { it > 0f }?.toDouble()

    fun setUsdInrRate(context: Context, rate: Double) {
        prefs(context).edit { putFloat(KEY_USD_INR_RATE, rate.toFloat()) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
