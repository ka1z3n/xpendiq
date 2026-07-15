package com.kaizenll.xpendiq.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.kaizenll.xpendiq.BuildConfig
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.util.Preferences

/**
 * Google Play Billing implementation of [BillingGateway] for the `play` flavor.
 *
 * Design: the manager stays the source of truth. This class only reconciles the *real* purchase
 * state into [Preferences.setSubscribed] and calls `entitlement.refresh()`. The app's existing
 * entitlement collector then unlocks any rows captured while expired. To avoid wiping the flag on
 * transient failures (offline, disconnect), it downgrades to unsubscribed **only** on a definitive
 * `OK` response that shows no active subscription.
 */
class PlayBillingGateway(private val app: XpendiqApplication) : BillingGateway {

    override val isAvailable: Boolean = true

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
        // USER_CANCELED and other codes: leave state untouched — the user simply dismissed Play.
    }

    private val client: BillingClient = BillingClient.newBuilder(app)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    private var connecting = false

    override fun start() = withConnection { reconcilePurchases() }

    override fun refreshPurchases() = withConnection { reconcilePurchases() }

    override fun launchPurchase(activity: Activity) = withConnection {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, productDetails ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails failed: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }
            val product = productDetails.firstOrNull() ?: run {
                Log.w(TAG, "No product details for $PRODUCT_ID — is it active in Play Console?")
                return@queryProductDetailsAsync
            }
            val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: run {
                Log.w(TAG, "No subscription offer for $PRODUCT_ID")
                return@queryProductDetailsAsync
            }
            launchFlow(activity, product, offerToken)
        }
    }

    private fun launchFlow(activity: Activity, product: ProductDetails, offerToken: String) {
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    /** Read the current subscription state from Play and mirror it into the entitlement flag. */
    private fun reconcilePurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val active = purchases.any { p ->
                p.products.contains(PRODUCT_ID) &&
                    p.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            // Acknowledge any freshly-bought purchase so Play doesn't auto-refund after 3 days.
            purchases.forEach { handlePurchase(it) }
            setSubscribed(active)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(PRODUCT_ID)) return
        if (!purchase.isAcknowledged) {
            val ack = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(ack) { /* best-effort; state is set regardless */ }
        }
        setSubscribed(true)
    }

    private fun setSubscribed(value: Boolean) {
        // In debug, don't let reconciliation flip the flag OFF — that would fight the Settings debug
        // entitlement override, which is the only way to test the unlock flow without a Play track.
        // A real purchase (value=true) still applies. Release builds reconcile both directions.
        if (!value && BuildConfig.DEBUG) return
        if (Preferences.isSubscribed(app) != value) {
            Preferences.setSubscribed(app, value)
        }
        // Always refresh: entitlement recomputes and the app-scope collector unlocks locked rows.
        app.entitlement.refresh()
    }

    /** Run [block] once the client is connected, reconnecting if the service dropped. */
    private fun withConnection(block: () -> Unit) {
        if (client.isReady) {
            block()
            return
        }
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    block()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false // Reconnect lazily on the next call.
            }
        })
    }

    companion object {
        private const val TAG = "PlayBilling"
        /** Subscription product id — must match the product created in Play Console. */
        const val PRODUCT_ID = "xpendiq_pro"
    }
}
