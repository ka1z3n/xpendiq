package com.kaizenll.xpendiq.billing

import com.kaizenll.xpendiq.XpendiqApplication

/** Full (off-Play) flavor: no billing backend — the app is free. */
object BillingFactory {
    fun create(app: XpendiqApplication): BillingGateway = NoopBillingGateway
}
