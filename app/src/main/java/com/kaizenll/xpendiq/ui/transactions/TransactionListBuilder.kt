package com.kaizenll.xpendiq.ui.transactions

import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.util.DateFormat
import com.kaizenll.xpendiq.util.Fx

object TransactionListBuilder {

    /**
     * Convert a date-sorted list of transactions + a category lookup into one [DaySection]
     * per calendar day. Each section carries its rows + daily total so the adapter can render
     * a filled card per day.
     */
    fun build(
        transactions: List<TransactionEntity>,
        categories: List<Category>,
    ): List<DaySection> {
        if (transactions.isEmpty()) return emptyList()
        val byId = categories.associateBy { it.id }
        val grouped = transactions.groupBy { DateFormat.dayKey(it.occurredAt) }
        val out = ArrayList<DaySection>(grouped.size)
        for ((dayKey, dayTxns) in grouped) {
            val first = dayTxns.first()
            out += DaySection(
                dayKey = dayKey,
                label = DateFormat.headerForDay(first.occurredAt),
                totalAmountPaise = dayTxns.sumOf {
                    Fx.inrEquivalentPaise(it.amountPaise, it.currency, it.amountInrPaise)
                },
                rows = dayTxns.map { TransactionListItem.Row(it, byId[it.categoryId]) },
            )
        }
        return out
    }
}
