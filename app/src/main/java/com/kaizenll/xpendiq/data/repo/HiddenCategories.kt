package com.kaizenll.xpendiq.data.repo

import com.kaizenll.xpendiq.data.dao.CategoryDao
import com.kaizenll.xpendiq.data.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The "Transfers (CC payment)" category represents a credit-card bill payment — an internal
 * transfer between the user's own accounts, not real income. It is excluded from the Credits
 * list and from "Received this month" totals (per spec §13).
 *
 * This helper resolves the category id once (it never changes after seeding) and exposes it
 * as a Flow so consumers can `flatMapLatest` cleanly.
 */
object HiddenCategories {

    private const val CC_PAYMENT_NAME = "Transfers (CC payment)"

    /** Emits -1L when the category isn't seeded yet, so DAOs can use it as a no-op sentinel. */
    fun ccPaymentCategoryIdFlow(dao: CategoryDao): Flow<Long> =
        dao.observeByType(TransactionType.CREDIT).map { list ->
            list.firstOrNull { it.name == CC_PAYMENT_NAME }?.id ?: SENTINEL_NONE
        }

    const val SENTINEL_NONE: Long = -1L
}
