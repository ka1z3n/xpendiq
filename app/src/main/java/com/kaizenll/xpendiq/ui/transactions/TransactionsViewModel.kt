package com.kaizenll.xpendiq.ui.transactions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.PaymentMode
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as XpendiqApplication
    private val repo = appCtx.repository
    private val txnDao = appCtx.database.transactionDao()
    private val categoryDao = appCtx.database.categoryDao()

    private val _selectedType = MutableStateFlow(TransactionType.DEBIT)
    val selectedType: StateFlow<TransactionType> = _selectedType

    /** Category-ids to keep. Empty = no filter (show all). Reset whenever the tab changes. */
    private val _categoryFilter = MutableStateFlow<Set<Long>>(emptySet())
    val categoryFilter: StateFlow<Set<Long>> = _categoryFilter

    /** Payment modes to keep. Empty = no filter (show all). Reset whenever the tab changes. */
    private val _paymentFilter = MutableStateFlow<Set<PaymentMode>>(emptySet())
    val paymentFilter: StateFlow<Set<PaymentMode>> = _paymentFilter

    private val ccPaymentIdFlow: Flow<Long> = HiddenCategories.ccPaymentCategoryIdFlow(categoryDao)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val txnsForType: Flow<List<TransactionEntity>> =
        combine(_selectedType, ccPaymentIdFlow) { type, excludeId -> type to excludeId }
            .flatMapLatest { (type, excludeId) ->
                // For CREDIT, hide the CC-bill-payment bucket (it's an internal transfer, not income).
                if (type == TransactionType.CREDIT) {
                    txnDao.observeByTypeExcludingCategory(type, excludeId)
                } else {
                    txnDao.observeByType(type)
                }
            }

    val items: StateFlow<List<DaySection>> =
        combine(
            txnsForType,
            categoryDao.observeAll(),
            _categoryFilter,
            _paymentFilter,
        ) { txns, cats, catFilter, payFilter ->
            var visible = txns
            if (catFilter.isNotEmpty()) visible = visible.filter { it.categoryId in catFilter }
            if (payFilter.isNotEmpty()) visible = visible.filter { it.paymentMode in payFilter }
            TransactionListBuilder.build(visible, cats)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Categories the user can filter by on the current tab, in display order. Started eagerly
     * because the filter dialog reads [StateFlow.value] imperatively on tap — a lazily-started
     * flow would still hold its initial empty list (nothing collects it).
     */
    val availableCategories: StateFlow<List<Category>> =
        combine(_selectedType, categoryDao.observeAll(), ccPaymentIdFlow) { type, cats, excludeId ->
            cats.filter { it.appliesToType == type && it.id != excludeId }
                .sortedBy { it.sortOrder }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Payment modes offered in the filter, in enum order. Modes that occur in the current tab's
     * data (no dead SMS-mode filters), plus CASH always — cash is manual-only (never parsed from
     * SMS), so it could never be discovered otherwise. Started eagerly so the sheet can read [value].
     */
    val availablePaymentModes: StateFlow<List<PaymentMode>> =
        txnsForType.map { txns ->
            val present = txns.mapTo(mutableSetOf()) { it.paymentMode }
            present.add(PaymentMode.CASH)
            PaymentMode.values().filter { it != PaymentMode.UNKNOWN && it in present }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSelectedType(type: TransactionType) {
        if (_selectedType.value == type) return
        _selectedType.value = type
        // Filters are scoped to the tab's data; start fresh on switch.
        _categoryFilter.value = emptySet()
        _paymentFilter.value = emptySet()
    }

    /** Apply both filter dimensions at once (from the filter sheet's Apply button). */
    fun setFilters(categories: Set<Long>, paymentModes: Set<PaymentMode>) {
        _categoryFilter.value = categories
        _paymentFilter.value = paymentModes
    }

    fun clearFilters() {
        _categoryFilter.value = emptySet()
        _paymentFilter.value = emptySet()
    }

    fun delete(txn: TransactionEntity) {
        viewModelScope.launch { repo.delete(txn) }
    }
}
