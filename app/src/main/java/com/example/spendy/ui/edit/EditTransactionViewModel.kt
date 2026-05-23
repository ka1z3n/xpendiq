package com.example.spendy.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendy.SpendyApplication
import com.example.spendy.data.entity.Category
import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionEntity
import com.example.spendy.data.entity.TransactionType
import com.example.spendy.util.ParseAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditTransactionViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as SpendyApplication
    private val repo = appCtx.repository
    private val txnDao = appCtx.database.transactionDao()
    private val categoryDao = appCtx.database.categoryDao()

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    private var mode: Mode = Mode.Add
    private var original: TransactionEntity? = null
    private var draft: Draft? = null

    /** Call once with a txnId > 0 to edit, or [TXN_ID_NEW] (-1) to add. */
    fun load(txnId: Long) {
        if (mode is Mode.Edit && (mode as Mode.Edit).txnId == txnId) return
        if (mode == Mode.Add && txnId < 0 && draft != null) return

        viewModelScope.launch {
            if (txnId < 0) loadAddDefaults() else loadExisting(txnId)
        }
    }

    private suspend fun loadExisting(txnId: Long) {
        mode = Mode.Edit(txnId)
        val txn = withContext(Dispatchers.IO) { txnDao.findById(txnId) }
        if (txn == null) {
            _state.value = UiState.NotFound
            return
        }
        original = txn
        draft = Draft.from(txn)
        val categories = withContext(Dispatchers.IO) {
            categoryDao.observeByType(txn.type).first()
        }
        _state.value = UiState.Ready(draft!!, categories)
    }

    private suspend fun loadAddDefaults() {
        mode = Mode.Add
        original = null
        val type = TransactionType.DEBIT
        val categories = withContext(Dispatchers.IO) {
            categoryDao.observeByType(type).first()
        }
        val uncatId = categories.firstOrNull { it.isSystem && it.name == "Uncategorized" }?.id ?: 0L
        draft = Draft(
            amountText = "",
            currency = "INR",
            merchant = "",
            categoryId = uncatId,
            paymentMode = PaymentMode.UNKNOWN,
            occurredAt = System.currentTimeMillis(),
            notes = "",
            type = type,
            smsBody = null,
            accountTail = null,
        )
        _state.value = UiState.Ready(draft!!, categories)
    }

    fun isAddMode(): Boolean = mode is Mode.Add

    fun setAmount(text: String) = mutate { it.copy(amountText = text) }
    fun setCurrency(currency: String) = mutate { it.copy(currency = currency) }
    fun setMerchant(text: String) = mutate { it.copy(merchant = text) }
    fun setCategoryId(id: Long) = mutate { it.copy(categoryId = id) }
    fun setPaymentMode(pm: PaymentMode) = mutate { it.copy(paymentMode = pm) }
    fun setOccurredAt(millis: Long) = mutate { it.copy(occurredAt = millis) }
    fun setNotes(text: String) = mutate { it.copy(notes = text) }

    /** Only valid in Add mode — switches the type, reloads categories, defaults to Uncategorized. */
    fun setType(newType: TransactionType) {
        if (mode !is Mode.Add) return
        val d = draft ?: return
        if (d.type == newType) return
        viewModelScope.launch {
            val categories = withContext(Dispatchers.IO) {
                categoryDao.observeByType(newType).first()
            }
            val uncatId = categories.firstOrNull { it.isSystem && it.name == "Uncategorized" }?.id ?: 0L
            draft = d.copy(type = newType, categoryId = uncatId)
            _state.value = UiState.Ready(draft!!, categories)
        }
    }

    private fun mutate(f: (Draft) -> Draft) {
        val d = draft ?: return
        draft = f(d)
        val ready = _state.value as? UiState.Ready ?: return
        _state.value = ready.copy(draft = draft!!)
    }

    fun save() {
        val d = draft ?: return
        val paise = ParseAmount.toPaise(d.amountText)
        if (paise == null || paise <= 0L) {
            _saveResult.value = SaveResult.InvalidAmount
            return
        }
        val merchant = d.merchant.trim().ifEmpty { null }
        viewModelScope.launch {
            when (val m = mode) {
                is Mode.Add -> {
                    repo.addManualTransaction(
                        amountPaise = paise,
                        currency = d.currency,
                        type = d.type,
                        paymentMode = d.paymentMode,
                        merchantRaw = merchant,
                        categoryId = d.categoryId,
                        occurredAt = d.occurredAt,
                        notes = d.notes.trim().ifEmpty { null },
                    )
                }
                is Mode.Edit -> {
                    val orig = original ?: return@launch
                    val updated = orig.copy(
                        amountPaise = paise,
                        currency = d.currency,
                        merchantRaw = merchant,
                        merchantNormalized = merchant?.uppercase()?.replace(Regex("\\s+"), ""),
                        categoryId = d.categoryId,
                        paymentMode = d.paymentMode,
                        occurredAt = d.occurredAt,
                        notes = d.notes.trim().ifEmpty { null },
                    )
                    repo.update(updated)
                }
            }
            _saveResult.value = SaveResult.Saved
        }
    }

    fun consumeSaveResult() { _saveResult.value = null }

    sealed interface Mode {
        data object Add : Mode
        data class Edit(val txnId: Long) : Mode
    }

    sealed interface UiState {
        data object Loading : UiState
        data object NotFound : UiState
        data class Ready(val draft: Draft, val categories: List<Category>) : UiState
    }

    data class Draft(
        val amountText: String,
        val currency: String,
        val merchant: String,
        val categoryId: Long,
        val paymentMode: PaymentMode,
        val occurredAt: Long,
        val notes: String,
        val type: TransactionType,
        val smsBody: String?,
        val accountTail: String?,
    ) {
        companion object {
            fun from(t: TransactionEntity): Draft = Draft(
                amountText = ParseAmount.formatPaise(t.amountPaise),
                currency = t.currency,
                merchant = t.merchantRaw.orEmpty(),
                categoryId = t.categoryId,
                paymentMode = t.paymentMode,
                occurredAt = t.occurredAt,
                notes = t.notes.orEmpty(),
                type = t.type,
                smsBody = t.smsBody,
                accountTail = t.accountTail,
            )
        }
    }

    sealed interface SaveResult {
        data object Saved : SaveResult
        data object InvalidAmount : SaveResult
    }

    companion object {
        const val TXN_ID_NEW: Long = -1L
    }
}
