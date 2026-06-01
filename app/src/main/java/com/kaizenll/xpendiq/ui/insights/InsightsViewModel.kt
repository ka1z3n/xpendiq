package com.kaizenll.xpendiq.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.data.repo.HiddenCategories
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class InsightsViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as XpendiqApplication
    private val txnDao = appCtx.database.transactionDao()
    private val categoryDao = appCtx.database.categoryDao()
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _selectedMonth = MutableStateFlow(YearMonth.now(zone))
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth

    val canGoNext: StateFlow<Boolean> = _selectedMonth
        .map { it.isBefore(YearMonth.now(zone)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<InsightsUi> =
        _selectedMonth.flatMapLatest { month -> uiFor(month) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUi.empty(_selectedMonth.value))

    fun nextMonth() {
        val now = YearMonth.now(zone)
        if (_selectedMonth.value.isBefore(now)) _selectedMonth.value = _selectedMonth.value.plusMonths(1)
    }

    fun prevMonth() {
        _selectedMonth.value = _selectedMonth.value.minusMonths(1)
    }

    private fun uiFor(month: YearMonth): kotlinx.coroutines.flow.Flow<InsightsUi> {
        val (currStart, currEnd) = monthBounds(month)
        val (prevStart, prevEnd) = monthBounds(month.minusMonths(1))

        // CC bill payments are excluded from "Received" — they're internal transfers, not income.
        val excludeIdFlow = HiddenCategories.ccPaymentCategoryIdFlow(categoryDao)

        @OptIn(ExperimentalCoroutinesApi::class)
        val creditedFlow = excludeIdFlow.flatMapLatest { excludeId ->
            txnDao.observeTotalExcludingCategory(TransactionType.CREDIT, currStart, currEnd, excludeId)
        }

        return combine(
            txnDao.observeTotal(TransactionType.DEBIT, currStart, currEnd),
            txnDao.observeTotal(TransactionType.DEBIT, prevStart, prevEnd),
            creditedFlow,
            txnDao.observeTotal(TransactionType.INVESTMENT, currStart, currEnd),
            categoryBars(currStart, currEnd),
        ) { spent, prevSpent, credited, invested, bars ->
            InsightsUi(
                month = month,
                totalSpent = spent,
                previousTotal = prevSpent,
                bars = bars,
                credited = credited,
                invested = invested,
            )
        }
    }

    private fun categoryBars(start: Long, end: Long): kotlinx.coroutines.flow.Flow<List<CategoryBar>> =
        txnDao.observeTotalsByCategory(TransactionType.DEBIT, start, end)
            .combine(categoryDao.observeAll()) { totals, cats ->
                val byId = cats.associateBy { it.id }
                totals.mapNotNull { t ->
                    val c = byId[t.categoryId] ?: return@mapNotNull null
                    CategoryBar(
                        categoryId = c.id,
                        name = c.name,
                        colorHex = c.colorHex,
                        totalPaise = t.totalPaise,
                    )
                }
            }

    private fun monthBounds(month: YearMonth): Pair<Long, Long> {
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.atDay(month.lengthOfMonth())
            .atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()
        return start to end
    }
}

data class InsightsUi(
    val month: YearMonth,
    val totalSpent: Long,
    val previousTotal: Long,
    val bars: List<CategoryBar>,
    val credited: Long,
    val invested: Long,
) {
    companion object {
        fun empty(month: YearMonth): InsightsUi = InsightsUi(
            month = month,
            totalSpent = 0L,
            previousTotal = 0L,
            bars = emptyList(),
            credited = 0L,
            invested = 0L,
        )
    }
}

data class CategoryBar(
    val categoryId: Long,
    val name: String,
    val colorHex: String,
    val totalPaise: Long,
)
