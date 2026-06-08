package com.kaizenll.xpendiq.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.data.entity.TransactionType
import java.time.LocalDate
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

        // Spend & received totals drop excluded-from-totals categories (self-transfers, CC-bill
        // payments) — internal money movement, not real spending or income.
        return combine(
            txnDao.observeTotalExcludingFlagged(TransactionType.DEBIT, currStart, currEnd),
            txnDao.observeTotalExcludingFlagged(TransactionType.DEBIT, prevStart, prevEnd),
            txnDao.observeTotalExcludingFlagged(TransactionType.CREDIT, currStart, currEnd),
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
                dailyAverage = dailyAverage(month, spent),
            )
        }
    }

    /**
     * Spend per day. For the current month we divide by days elapsed so the average isn't
     * deflated by days that haven't happened yet; past months use their full length.
     */
    private fun dailyAverage(month: YearMonth, spent: Long): Long {
        val today = LocalDate.now(zone)
        val days = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
        return if (days > 0) spent / days else 0L
    }

    private fun categoryBars(start: Long, end: Long): kotlinx.coroutines.flow.Flow<List<CategoryBar>> =
        txnDao.observeTotalsByCategoryExcludingFlagged(TransactionType.DEBIT, start, end)
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
    val dailyAverage: Long,
) {
    companion object {
        fun empty(month: YearMonth): InsightsUi = InsightsUi(
            month = month,
            totalSpent = 0L,
            previousTotal = 0L,
            bars = emptyList(),
            credited = 0L,
            invested = 0L,
            dailyAverage = 0L,
        )
    }
}

data class CategoryBar(
    val categoryId: Long,
    val name: String,
    val colorHex: String,
    val totalPaise: Long,
)
