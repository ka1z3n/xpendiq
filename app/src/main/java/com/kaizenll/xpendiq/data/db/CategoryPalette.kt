package com.kaizenll.xpendiq.data.db

import com.kaizenll.xpendiq.data.entity.TransactionType

/**
 * Per-category default colours. Used by both the seeder (new installs) and the color migration
 * (existing installs that still have the old generic defaults).
 */
object CategoryPalette {

    val DEBIT: Map<String, String> = mapOf(
        "Food & Dining" to "#FF5722",   // deep orange
        "Groceries" to "#4CAF50",       // green
        "Transport" to "#2196F3",       // blue
        "Shopping" to "#9C27B0",        // purple
        "Subscriptions" to "#673AB7",   // deep purple
        "Bills & Utilities" to "#009688", // teal
        "Entertainment" to "#E91E63",   // pink
        "Health" to "#F44336",          // red
        "Travel" to "#00BCD4",          // cyan
        "Rent" to "#795548",            // brown
        "Transfers" to "#607D8B",       // blue grey
        "Other" to "#9E9E9E",           // grey
    )

    val CREDIT: Map<String, String> = mapOf(
        "Refund" to "#4CAF50",                 // green
        "Transfers (P2P UPI in)" to "#8BC34A", // light green
        "Transfers (CC payment)" to "#607D8B", // blue grey (hidden in lists)
        "Income" to "#FFC107",                 // amber
        "Cashback / Rewards" to "#FF9800",     // orange
        "Interest" to "#2196F3",               // blue
        "Cheque / NEFT deposit" to "#00BCD4",  // cyan
        "Other" to "#9E9E9E",                  // grey
    )

    val INVESTMENT: Map<String, String> = mapOf(
        "Investment" to "#3F51B5",  // indigo
    )

    /** Always grey, across all types. */
    const val UNCATEGORIZED: String = "#9E9E9E"

    /** Fallback for names not in the map (e.g. user-added categories). */
    const val FALLBACK: String = "#607D8B"

    fun colorFor(name: String, type: TransactionType): String {
        val map = when (type) {
            TransactionType.DEBIT -> DEBIT
            TransactionType.CREDIT -> CREDIT
            TransactionType.INVESTMENT -> INVESTMENT
        }
        return map[name] ?: FALLBACK
    }
}
