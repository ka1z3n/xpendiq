package com.example.spendy.ui.investments

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendy.SpendyApplication
import com.example.spendy.data.entity.TransactionType
import com.example.spendy.ui.transactions.TransactionListBuilder
import com.example.spendy.ui.transactions.TransactionListItem
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class InvestmentsViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as SpendyApplication
    private val txnDao = appCtx.database.transactionDao()
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

    val monthlyTotal: StateFlow<Long> =
        txnDao.observeTotal(TransactionType.INVESTMENT, monthStart, monthEnd)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val items: StateFlow<List<TransactionListItem>> =
        txnDao.observeByType(TransactionType.INVESTMENT)
            .combine(categoryDao.observeAll()) { txns, cats -> TransactionListBuilder.build(txns, cats) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
