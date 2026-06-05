package com.kaizenll.xpendiq.ui.transactions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.data.repo.HiddenCategories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as XpendiqApplication
    private val repo = appCtx.repository
    private val txnDao = appCtx.database.transactionDao()
    private val categoryDao = appCtx.database.categoryDao()

    private val _selectedType = MutableStateFlow(TransactionType.DEBIT)
    val selectedType: StateFlow<TransactionType> = _selectedType

    private val ccPaymentIdFlow: Flow<Long> = HiddenCategories.ccPaymentCategoryIdFlow(categoryDao)

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<DaySection>> =
        combine(_selectedType, ccPaymentIdFlow) { type, excludeId -> type to excludeId }
            .flatMapLatest { (type, excludeId) ->
                // For CREDIT, hide the CC-bill-payment bucket (it's an internal transfer, not income).
                if (type == TransactionType.CREDIT) {
                    txnDao.observeByTypeExcludingCategory(type, excludeId)
                } else {
                    txnDao.observeByType(type)
                }
            }
            .combine(categoryDao.observeAll()) { txns, cats -> TransactionListBuilder.build(txns, cats) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSelectedType(type: TransactionType) {
        _selectedType.value = type
    }

    fun delete(txn: TransactionEntity) {
        viewModelScope.launch { repo.delete(txn) }
    }
}
