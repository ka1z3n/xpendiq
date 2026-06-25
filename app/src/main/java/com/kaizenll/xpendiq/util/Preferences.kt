package com.kaizenll.xpendiq.util

import android.content.Context
import androidx.core.content.edit

object Preferences {
    private const val PREFS = "xpendiq_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"
    private const val KEY_APP_LOCK = "app_lock_enabled"
    private const val KEY_USD_INR_RATE = "usd_inr_rate"
    private const val KEY_USD_INR_RATE_SET_AT = "usd_inr_rate_set_at"
    private const val KEY_TRIAL_START = "trial_start_millis"
    private const val KEY_SUBSCRIBED = "subscribed"

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

    /** Epoch millis the rate was last set, or 0 if never (or set before this was tracked). */
    fun getUsdInrRateSetAt(context: Context): Long =
        prefs(context).getLong(KEY_USD_INR_RATE_SET_AT, 0L)

    /** Stores the rate and stamps [setAtMillis] so the UI can flag a stale rate later. */
    fun setUsdInrRate(context: Context, rate: Double, setAtMillis: Long) {
        prefs(context).edit {
            putFloat(KEY_USD_INR_RATE, rate.toFloat())
            putLong(KEY_USD_INR_RATE_SET_AT, setAtMillis)
        }
    }

    /**
     * Epoch millis the free trial started (first launch). Stamps [now] on first read so the trial
     * clock begins the moment the app is first opened. Later replaced by Play Billing's trial.
     */
    fun ensureTrialStart(context: Context, now: Long): Long {
        val existing = prefs(context).getLong(KEY_TRIAL_START, 0L)
        if (existing > 0L) return existing
        prefs(context).edit { putLong(KEY_TRIAL_START, now) }
        return now
    }

    fun getTrialStart(context: Context): Long = prefs(context).getLong(KEY_TRIAL_START, 0L)

    /** Debug/billing hook: move the trial start (e.g. to simulate "day 25" or an expired trial). */
    fun setTrialStart(context: Context, millis: Long) {
        prefs(context).edit { putLong(KEY_TRIAL_START, millis) }
    }

    /** Whether the user holds an active subscription. For now toggled by debug/billing later. */
    fun isSubscribed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SUBSCRIBED, false)

    fun setSubscribed(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SUBSCRIBED, value) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
