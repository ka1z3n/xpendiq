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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
     * Exact-match suggestions: for each merchant that has uncategorized rows AND a history of
     * being categorized by the user, propose the category the user filed it under most often.
     * No fuzzy matching — Indian P2P merchants (people, auto-rickshaws) are too ambiguous to
     * guess; we only ever echo a decision the user already made for that exact merchant key.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<List<Suggestion>> =
        _selectedType.flatMapLatest { type ->
            uncategorizedIdFlow(type).flatMapLatest { uncatId ->
                if (uncatId == null) flowOf(emptyList())
                else combine(
                    txnDao.observeUncategorized(type, uncatId),
                    txnDao.observeByTypeExcludingCategory(type, uncatId),
                    categoryDao.observeAll(),
                ) { uncats, history, cats -> buildSuggestions(uncats, history, cats) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildSuggestions(
        uncats: List<TransactionEntity>,
        history: List<TransactionEntity>,
        cats: List<Category>,
    ): List<Suggestion> {
        if (uncats.isEmpty() || history.isEmpty()) return emptyList()
        val byId = cats.associateBy { it.id }
        val historyByMerchant = history
            .filter { !it.merchantNormalized.isNullOrBlank() }
            .groupBy { it.merchantNormalized!! }
        val uncatByMerchant = uncats
            .filter { !it.merchantNormalized.isNullOrBlank() }
            .groupBy { it.merchantNormalized!! }

        val out = ArrayList<Suggestion>()
        for ((merchant, rows) in uncatByMerchant) {
            val hist = historyByMerchant[merchant] ?: continue
            // Dominant = the category this merchant was filed under most often.
            val dominantId = hist.groupingBy { it.categoryId }.eachCount()
                .maxByOrNull { it.value }?.key ?: continue
            val category = byId[dominantId] ?: continue
            val display = rows.firstOrNull { !it.merchantRaw.isNullOrBlank() }?.merchantRaw ?: merchant
            out += Suggestion(merchant, display, rows.size, category)
        }
        // Most-impactful first (clears the largest pile), capped so the block stays a glance.
        return out.sortedByDescending { it.count }.take(3)
    }

    /** Resolves the Uncategorized category id for [type] as a flow (null until categories load). */
    private fun uncategorizedIdFlow(type: TransactionType): Flow<Long?> =
        categoryDao.observeByType(type).map { list ->
            list.firstOrNull { it.isSystem && it.name == "Uncategorized" }?.id
        }

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

    /**
     * Accept a suggestion: writes a permanent merchant rule (so future SMS auto-file) and
     * recategorizes every matching uncategorized row in one shot.
     */
    fun applySuggestion(s: Suggestion) {
        viewModelScope.launch {
            repo.applyMerchantRuleAndRecategorize(s.merchantNormalized, _selectedType.value, s.category.id)
        }
    }

    fun delete(txn: TransactionEntity) {
        viewModelScope.launch { repo.delete(txn) }
    }

    /** Assign [category] to every selected transaction (bulk multi-select). */
    fun bulkRecategorize(ids: List<Long>, category: Category) {
        viewModelScope.launch { repo.recategorizeAll(ids, category.id) }
    }

    /** Delete every selected transaction (bulk multi-select). */
    fun bulkDelete(ids: List<Long>) {
        viewModelScope.launch { repo.deleteAll(ids) }
    }

    /**
     * One exact-match suggestion: [count] uncategorized rows from [merchantNormalized] (shown as
     * [display], the raw merchant name) that the user has historically filed under [category].
     */
    data class Suggestion(
        val merchantNormalized: String,
        val display: String,
        val count: Int,
        val category: Category,
    )

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
