package com.kaizenll.xpendiq.ui.transactions

import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.TransactionEntity

/** A single transaction row. Used inside [DaySection] and by the Home "Recent" block. */
sealed interface TransactionListItem {
    val stableId: String

    data class Row(
        val txn: TransactionEntity,
        val category: Category?,
    ) : TransactionListItem {
        override val stableId: String get() = "r:${txn.id}"
    }
}

/** One calendar day rendered as a filled card: header (label + total) over its rows. */
data class DaySection(
    val dayKey: Long,
    val label: String,
    val totalAmountPaise: Long,
    val rows: List<TransactionListItem.Row>,
) {
    val stableId: String get() = "d:$dayKey"
}
