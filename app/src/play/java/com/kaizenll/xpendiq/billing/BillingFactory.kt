package com.kaizenll.xpendiq.billing

import com.kaizenll.xpendiq.XpendiqApplication

/** Play flavor: real Google Play Billing behind the entitlement seam. */
object BillingFactory {
    fun create(app: XpendiqApplication): BillingGateway = PlayBillingGateway(app)
}
