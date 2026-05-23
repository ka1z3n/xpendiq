package com.example.spendy.ui.transactions

import com.example.spendy.data.entity.Category
import com.example.spendy.data.entity.TransactionEntity

sealed interface TransactionListItem {
    val stableId: String

    data class Header(
        val dayKey: Long,
        val label: String,
        val totalAmountPaise: Long,
    ) : TransactionListItem {
        override val stableId: String get() = "h:$dayKey"
    }

    data class Row(
        val txn: TransactionEntity,
        val category: Category?,
    ) : TransactionListItem {
        override val stableId: String get() = "r:${txn.id}"
    }
}
