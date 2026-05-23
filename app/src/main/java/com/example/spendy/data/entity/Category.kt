package com.example.spendy.data.entity

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
) {
    companion object {
        const val ICON_UNCATEGORIZED = "help_outline"
        const val ICON_OTHER = "category"
    }
}
