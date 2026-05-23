package com.example.spendy.ui.uncategorized

import com.example.spendy.data.entity.Category
import com.example.spendy.data.entity.TransactionType

sealed interface PickerItem {
    val stableId: String

    data class Header(val type: TransactionType, val title: String) : PickerItem {
        override val stableId: String get() = "h:${type.name}"
    }

    data class Row(val category: Category) : PickerItem {
        override val stableId: String get() = "c:${category.id}"
    }
}
