package com.example.spendy.ui.transactions

import com.example.spendy.data.entity.Category
import com.example.spendy.data.entity.TransactionEntity
import com.example.spendy.util.DateFormat

object TransactionListBuilder {

    /**
     * Convert a date-sorted list of transactions + a category lookup into
     * a flat list of `Header` and `Row` items grouped by calendar day.
     */
    fun build(
        transactions: List<TransactionEntity>,
        categories: List<Category>,
    ): List<TransactionListItem> {
        if (transactions.isEmpty()) return emptyList()
        val byId = categories.associateBy { it.id }
        val grouped = transactions.groupBy { DateFormat.dayKey(it.occurredAt) }
        val out = ArrayList<TransactionListItem>(transactions.size + grouped.size)
        for ((dayKey, dayTxns) in grouped) {
            val first = dayTxns.first()
            val total = dayTxns.sumOf { it.amountPaise }
            out += TransactionListItem.Header(
                dayKey = dayKey,
                label = DateFormat.headerForDay(first.occurredAt),
                totalAmountPaise = total,
            )
            for (t in dayTxns) {
                out += TransactionListItem.Row(t, byId[t.categoryId])
            }
        }
        return out
    }
}
