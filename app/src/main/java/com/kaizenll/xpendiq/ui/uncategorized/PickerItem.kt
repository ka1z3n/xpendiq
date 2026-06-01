package com.kaizenll.xpendiq.ui.uncategorized

import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.TransactionType

sealed interface PickerItem {
    val stableId: String

    data class Header(val type: TransactionType, val title: String) : PickerItem {
        override val stableId: String get() = "h:${type.name}"
    }

    data class Row(val category: Category) : PickerItem {
        override val stableId: String get() = "c:${category.id}"
    }
}
