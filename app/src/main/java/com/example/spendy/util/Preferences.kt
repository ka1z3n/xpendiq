package com.example.spendy.util

import android.content.Context
import androidx.core.content.edit

object Preferences {
    private const val PREFS = "spendy_prefs"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    fun isOnboardingComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingComplete(context: Context, value: Boolean = true) {
        prefs(context).edit { putBoolean(KEY_ONBOARDING_DONE, value) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
