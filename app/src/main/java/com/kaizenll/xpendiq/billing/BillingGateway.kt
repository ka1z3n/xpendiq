package com.kaizenll.xpendiq.billing

import android.app.Activity

/**
 * Purchase surface for the subscription. Flavor-specific: the `play` build backs this with Google
 * Play Billing; the `full` build (sideloaded, free) uses a no-op. Whichever it is, it never
 * computes entitlement itself — it writes [com.kaizenll.xpendiq.util.Preferences.setSubscribed] and
 * calls [com.kaizenll.xpendiq.entitlement.EntitlementManager.refresh], keeping the manager the
 * single source of truth.
 *
 * Obtain the flavor's implementation from `BillingFactory.create(app)` (provided per source set).
 */
interface BillingGateway {

    /** Connect to the billing service (if any) and reconcile purchases on startup. Idempotent. */
    fun start()

    /** Re-query purchases to catch renewals, cancellations, or refunds. Call on app foreground. */
    fun refreshPurchases()

    /** Launch the Play purchase flow for the annual subscription. No-op when billing is unavailable. */
    fun launchPurchase(activity: Activity)

    /** Whether a real billing backend is present (false for the free `full` flavor). */
    val isAvailable: Boolean
}

/** Free / off-Play flavor: nothing to purchase, everyone is already entitled. */
object NoopBillingGateway : BillingGateway {
    override fun start() {}
    override fun refreshPurchases() {}
    override fun launchPurchase(activity: Activity) {}
    override val isAvailable: Boolean = false
}
