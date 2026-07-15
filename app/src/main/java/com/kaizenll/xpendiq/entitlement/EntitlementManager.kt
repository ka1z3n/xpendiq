package com.kaizenll.xpendiq.entitlement

import android.content.Context
import com.kaizenll.xpendiq.BuildConfig
import com.kaizenll.xpendiq.util.Preferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the user's [EntitlementState]. Computes it from the local trial clock
 * ([Preferences.ensureTrialStart]) and the subscription flag. [refresh] recomputes (call on app
 * foreground and after a debug toggle). This is the seam Play Billing will plug into later — only
 * the computation changes, not the callers.
 */
class EntitlementManager(private val context: Context) {

    private val _state = MutableStateFlow<EntitlementState>(compute(System.currentTimeMillis()))
    val state: StateFlow<EntitlementState> = _state

    /** Recompute against the current time. Safe to call often; emits only on change. */
    fun refresh() {
        _state.value = compute(System.currentTimeMillis())
    }

    /** Imperative read for non-flow callers (e.g. the repository deciding a row's lock state). */
    fun isEntitled(): Boolean = compute(System.currentTimeMillis()).isEntitled

    private fun compute(now: Long): EntitlementState {
        // The off-Play `full` flavor can't take payment (sideloaded), so it's free forever — no
        // trial clock, no paywall, no locking. Only the `play` flavor runs the trial/subscription.
        if (!BuildConfig.BILLING_ENABLED) return EntitlementState.Subscribed
        if (Preferences.isSubscribed(context)) return EntitlementState.Subscribed
        val start = Preferences.ensureTrialStart(context, now)
        val daysElapsed = ((now - start) / DAY_MILLIS).toInt()
        val daysLeft = TRIAL_DAYS - daysElapsed
        return if (daysLeft >= 1) EntitlementState.InTrial(daysLeft) else EntitlementState.Expired
    }

    companion object {
        const val TRIAL_DAYS = 30
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
