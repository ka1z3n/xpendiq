package com.kaizenll.xpendiq.entitlement

/**
 * The user's access level. Drives read-only gating, the lock flag on newly captured rows, and the
 * trial banner/paywall. Source-agnostic: today it's computed from a local trial clock + a debug
 * toggle; later Google Play Billing fills the same states behind [EntitlementManager].
 */
sealed interface EntitlementState {
    /** Within the free trial; [daysLeft] is >= 1. */
    data class InTrial(val daysLeft: Int) : EntitlementState
    /** Trial elapsed and no active subscription — read-only, new captures get locked. */
    data object Expired : EntitlementState
    /** Active paid subscription — full access. */
    data object Subscribed : EntitlementState

    /** Full access: can add/edit, and new captures are stored unlocked. Only [Expired] is false. */
    val isEntitled: Boolean get() = this !is Expired
}
