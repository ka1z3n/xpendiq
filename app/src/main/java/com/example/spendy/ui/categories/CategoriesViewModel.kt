package com.example.spendy.ui.categories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendy.SpendyApplication
import com.example.spendy.data.entity.Category
import com.example.spendy.data.entity.TransactionType
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as SpendyApplication
    private val categoryDao = appCtx.database.categoryDao()
    private val txnDao = appCtx.database.transactionDao()
    private val categoryRepo = appCtx.categoryRepository

    private val monthStart: Long
    private val monthEnd: Long

    init {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val start = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
        val end = now.toLocalDate().with(TemporalAdjusters.lastDayOfMonth())
            .atTime(23, 59, 59, 999_000_000).atZone(zone)
        monthStart = start.toInstant().toEpochMilli()
        monthEnd = end.toInstant().toEpochMilli()
    }

    val items: StateFlow<List<CategoriesListItem>> =
        combine(
            categoryDao.observeAll(),
            txnDao.observeTotalsByCategory(TransactionType.DEBIT, monthStart, monthEnd),
            txnDao.observeTotalsByCategory(TransactionType.CREDIT, monthStart, monthEnd),
            txnDao.observeTotalsByCategory(TransactionType.INVESTMENT, monthStart, monthEnd),
        ) { cats, debitTotals, creditTotals, investTotals ->
            buildList(cats, debitTotals + creditTotals + investTotals)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<CategoryEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CategoryEvent> = _events.asSharedFlow()

    fun addCategory(name: String, type: TransactionType, colorHex: String) {
        if (name.isBlank()) {
            viewModelScope.launch { _events.emit(CategoryEvent.NameBlank) }
            return
        }
        viewModelScope.launch {
            categoryRepo.add(name = name.trim(), type = type, colorHex = colorHex, iconKey = "category")
            _events.emit(CategoryEvent.Saved)
        }
    }

    fun updateCategory(category: Category, name: String, colorHex: String) {
        if (name.isBlank()) {
            viewModelScope.launch { _events.emit(CategoryEvent.NameBlank) }
            return
        }
        viewModelScope.launch {
            categoryRepo.update(category, name = name, colorHex = colorHex)
            _events.emit(CategoryEvent.Saved)
        }
    }

    suspend fun usageCount(category: Category): Int = categoryRepo.usageCount(category.id)

    suspend fun reassignmentOptions(category: Category): List<Category> =
        categoryRepo.reassignmentOptions(category.appliesToType, excludeId = category.id)

    fun deleteCategory(category: Category, reassignToId: Long) {
        viewModelScope.launch {
            categoryRepo.delete(category, reassignToId)
            _events.emit(CategoryEvent.Deleted)
        }
    }

    private fun buildList(
        all: List<Category>,
        totals: List<com.example.spendy.data.dao.CategoryTotal>,
    ): List<CategoriesListItem> {
        val byCategory = totals.associate { it.categoryId to it.totalPaise }
        val grouped = all.groupBy { it.appliesToType }
        val out = ArrayList<CategoriesListItem>()
        for (type in TransactionType.values()) {
            val rows = grouped[type].orEmpty().sortedBy { it.sortOrder }
            if (rows.isEmpty()) continue
            out += CategoriesListItem.Header(type, sectionTitle(type))
            for (c in rows) out += CategoriesListItem.Row(c, byCategory[c.id] ?: 0L)
        }
        return out
    }

    private fun sectionTitle(type: TransactionType): String = when (type) {
        TransactionType.DEBIT -> "Spends"
        TransactionType.CREDIT -> "Credits"
        TransactionType.INVESTMENT -> "Investments"
    }
}

sealed interface CategoryEvent {
    data object Saved : CategoryEvent
    data object Deleted : CategoryEvent
    data object NameBlank : CategoryEvent
}
