package com.kaizenll.xpendiq.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index("appliesToType")],
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val isSystem: Boolean,
    val sortOrder: Int,
    val appliesToType: TransactionType,
    /**
     * When true, transactions in this category are left out of spend/received totals and the
     * Insights breakdown — they're internal money movement, not real spending or income
     * (e.g. a self-transfer between your own accounts, or a credit-card bill payment).
     */
    val excludedFromTotals: Boolean = false,
) {
    companion object {
        const val ICON_UNCATEGORIZED = "help_outline"
        const val ICON_OTHER = "category"
    }
}
