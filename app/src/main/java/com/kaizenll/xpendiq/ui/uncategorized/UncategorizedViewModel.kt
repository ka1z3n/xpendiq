package com.kaizenll.xpendiq.ui.uncategorized

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.data.entity.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UncategorizedViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as XpendiqApplication
    private val repo = appCtx.repository
    private val txnDao = appCtx.database.transactionDao()
    private val categoryDao = appCtx.database.categoryDao()

    private val _selectedType = MutableStateFlow(TransactionType.DEBIT)
    val selectedType: StateFlow<TransactionType> = _selectedType

    // Lazily resolved Uncategorized category id, per type, cached after first observation.
    private val uncategorizedIds = mutableMapOf<TransactionType, Long>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<TransactionEntity>> =
        _selectedType.flatMapLatest { type -> observeUncategorizedTxns(type) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Picker items: every category (across DEBIT / CREDIT / INVESTMENT) grouped by type, with
     * the Uncategorized bucket of each type filtered out. Lets the user move a row across
     * types — e.g. an Uncategorized DEBIT into the Investment bucket.
     */
    val pickableItems: StateFlow<List<PickerItem>> =
        categoryDao.observeAll().map { all -> buildPickerItems(all) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildPickerItems(all: List<Category>): List<PickerItem> {
        val byType = all
            .filterNot { it.isSystem && it.name == "Uncategorized" }
            .groupBy { it.appliesToType }
        val out = ArrayList<PickerItem>()
        for (type in TransactionType.values()) {
            val rows = byType[type].orEmpty().sortedBy { it.sortOrder }
            if (rows.isEmpty()) continue
            out += PickerItem.Header(type, sectionTitle(type))
            for (c in rows) out += PickerItem.Row(c)
        }
        return out
    }

    private fun sectionTitle(type: TransactionType): String = when (type) {
        TransactionType.DEBIT -> "Spends"
        TransactionType.CREDIT -> "Credits"
        TransactionType.INVESTMENT -> "Investments"
    }

    fun setSelectedType(type: TransactionType) {
        _selectedType.value = type
    }

    fun recategorize(txn: TransactionEntity, category: Category, applyToAll: Boolean) {
        viewModelScope.launch {
            val merchant = txn.merchantNormalized
            if (applyToAll && !merchant.isNullOrBlank()) {
                repo.applyMerchantRuleAndRecategorize(merchant, txn.type, category.id)
            } else {
                repo.recategorize(txn, category.id)
            }
        }
    }

    fun delete(txn: TransactionEntity) {
        viewModelScope.launch { repo.delete(txn) }
    }

    private fun observeUncategorizedTxns(type: TransactionType): Flow<List<TransactionEntity>> {
        val cachedId = uncategorizedIds[type]
        return if (cachedId != null) {
            txnDao.observeUncategorized(type, cachedId)
        } else {
            // First call: look up the Uncategorized category id, then start the flow.
            @OptIn(ExperimentalCoroutinesApi::class)
            categoryDao.observeByType(type)
                .map { list -> list.firstOrNull { it.isSystem && it.name == "Uncategorized" }?.id }
                .flatMapLatest { id ->
                    if (id == null) emptyFlow() else {
                        uncategorizedIds[type] = id
                        txnDao.observeUncategorized(type, id)
                    }
                }
        }
    }
}
