package com.kaizenll.xpendiq.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.ui.insights.CategoryBar
import com.kaizenll.xpendiq.ui.transactions.TransactionListItem
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as XpendiqApplication
    private val dao = appCtx.database.transactionDao()
    private val categoryDao = appCtx.database.categoryDao()

    private val monthStart: Long
    private val monthEnd: Long

    init {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
        val endOfMonth = now.toLocalDate().with(TemporalAdjusters.lastDayOfMonth())
            .atTime(23, 59, 59, 999_000_000).atZone(zone)
        monthStart = startOfMonth.toInstant().toEpochMilli()
        monthEnd = endOfMonth.toInstant().toEpochMilli()
    }

    // Spend/received totals drop categories flagged excluded-from-totals (self-transfers and
    // CC-bill payments) — internal money movement, not real spending or income.
    val monthSpend: Flow<Long> = dao.observeTotalExcludingFlagged(TransactionType.DEBIT, monthStart, monthEnd)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val monthCredited: Flow<Long> =
        dao.observeTotalExcludingFlagged(TransactionType.CREDIT, monthStart, monthEnd)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val monthInvested: Flow<Long> = dao.observeTotal(TransactionType.INVESTMENT, monthStart, monthEnd)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Combined count of uncategorized DEBIT + CREDIT rows. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uncategorizedCount: Flow<Int> =
        combine(uncategorizedCountFor(TransactionType.DEBIT), uncategorizedCountFor(TransactionType.CREDIT)) { d, c -> d + c }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun uncategorizedCountFor(type: TransactionType): Flow<Int> =
        categoryDao.observeByType(type)
            .map { list -> list.firstOrNull { it.isSystem && it.name == "Uncategorized" }?.id }
            .flatMapLatest { id ->
                if (id == null) flowOf(0) else dao.observeUncategorizedCount(type, id)
            }

    /** Top 3 spend categories this month, sized descending. */
    val topSpendCategories: Flow<List<CategoryBar>> =
        dao.observeTotalsByCategoryExcludingFlagged(TransactionType.DEBIT, monthStart, monthEnd)
            .combine(categoryDao.observeAll()) { totals, cats ->
                val byId = cats.associateBy { it.id }
                totals.take(3).mapNotNull { t ->
                    val c = byId[t.categoryId] ?: return@mapNotNull null
                    CategoryBar(
                        categoryId = c.id,
                        name = c.name,
                        colorHex = c.colorHex,
                        totalPaise = t.totalPaise,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Five most recent debits, with their category resolved. */
    val recentDebits: Flow<List<TransactionListItem.Row>> =
        dao.observeRecent(TransactionType.DEBIT, RECENT_LIMIT)
            .combine(categoryDao.observeAll()) { txns, cats ->
                val byId = cats.associateBy { it.id }
                txns.map { TransactionListItem.Row(it, byId[it.categoryId]) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
