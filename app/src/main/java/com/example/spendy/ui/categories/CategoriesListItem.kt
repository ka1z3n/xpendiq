package com.example.spendy.ui.categories

import com.example.spendy.data.entity.Category
import com.example.spendy.data.entity.TransactionType

sealed interface CategoriesListItem {
    val stableId: String

    data class Header(val type: TransactionType, val title: String) : CategoriesListItem {
        override val stableId: String get() = "h:${type.name}"
    }

    data class Row(val category: Category, val monthlyPaise: Long) : CategoriesListItem {
        override val stableId: String get() = "r:${category.id}"
    }
}
