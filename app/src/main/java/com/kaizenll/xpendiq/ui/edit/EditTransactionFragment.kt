package com.kaizenll.xpendiq.ui.edit

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.ui.edit.EditTransactionViewModel.SaveResult
import com.kaizenll.xpendiq.ui.edit.EditTransactionViewModel.UiState
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class EditTransactionFragment : Fragment(R.layout.fragment_edit_transaction) {

    private val viewModel: EditTransactionViewModel by viewModels()
    private val zone = ZoneId.systemDefault()
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

    private var binding: Holder? = null
    private var suppressTextWatchers = false
    private var suppressListeners = false

    /** Category ids currently rendered as chips, so we only rebuild when the set changes. */
    private var renderedCategoryIds: List<Long> = emptyList()


    private class Holder(view: View) {
        val toolbar: MaterialToolbar = view.findViewById(R.id.toolbar)
        val typeToggle: View = view.findViewById(R.id.type_toggle)
        val typeSpends: TextView = view.findViewById(R.id.btn_type_spends)
        val typeCredits: TextView = view.findViewById(R.id.btn_type_credits)
        val typeInvestments: TextView = view.findViewById(R.id.btn_type_investments)
        val currencyBtn: MaterialButton = view.findViewById(R.id.currency_btn)
        val amountSymbol: TextView = view.findViewById(R.id.amount_symbol)
        val amount: EditText = view.findViewById(R.id.amount)
        val merchant: TextInputEditText = view.findViewById(R.id.merchant)
        val categoryChips: ChipGroup = view.findViewById(R.id.category_chips)
        val paymentToggle: View = view.findViewById(R.id.payment_toggle)
        val pmUpi: TextView = view.findViewById(R.id.pm_upi)
        val pmCredit: TextView = view.findViewById(R.id.pm_credit)
        val pmDebit: TextView = view.findViewById(R.id.pm_debit)
        val pmCash: TextView = view.findViewById(R.id.pm_cash)
        val dateBtn: MaterialButton = view.findViewById(R.id.date_btn)
        val notes: TextInputEditText = view.findViewById(R.id.notes)
        val smsSection: LinearLayout = view.findViewById(R.id.sms_section)
        val smsBody: TextView = view.findViewById(R.id.sms_body)
        val saveBtn: MaterialButton = view.findViewById(R.id.save_btn)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val txnId = arguments?.getLong(ARG_TXN_ID, EditTransactionViewModel.TXN_ID_NEW)
            ?: EditTransactionViewModel.TXN_ID_NEW
        viewModel.load(txnId)

        val h = Holder(view).also { binding = it }

        h.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        h.saveBtn.setOnClickListener { viewModel.save() }

        h.amount.addTextChangedListener(simpleWatcher { if (!suppressTextWatchers) viewModel.setAmount(it) })
        h.merchant.addTextChangedListener(simpleWatcher { if (!suppressTextWatchers) viewModel.setMerchant(it) })
        h.notes.addTextChangedListener(simpleWatcher { if (!suppressTextWatchers) viewModel.setNotes(it) })

        h.typeSpends.setOnClickListener { viewModel.setType(TransactionType.DEBIT) }
        h.typeCredits.setOnClickListener { viewModel.setType(TransactionType.CREDIT) }
        h.typeInvestments.setOnClickListener { viewModel.setType(TransactionType.INVESTMENT) }
        h.pmUpi.setOnClickListener { onPaymentClick(PaymentMode.UPI) }
        h.pmCredit.setOnClickListener { onPaymentClick(PaymentMode.CARD_CREDIT) }
        h.pmDebit.setOnClickListener { onPaymentClick(PaymentMode.CARD_DEBIT) }
        h.pmCash.setOnClickListener { onPaymentClick(PaymentMode.CASH) }
        h.categoryChips.setOnCheckedStateChangeListener { group, checkedIds ->
            if (suppressListeners) return@setOnCheckedStateChangeListener
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
            (chip?.tag as? Long)?.let { viewModel.setCategoryId(it) }
        }

        h.currencyBtn.setOnClickListener { showCurrencyMenu(h.currencyBtn) }
        h.dateBtn.setOnClickListener { showDatePicker() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { renderState(h, it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.saveResult.collect { result ->
                    if (result != null) handleSaveResult(result)
                }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun renderState(h: Holder, state: UiState) {
        when (state) {
            is UiState.Loading -> Unit
            is UiState.NotFound -> {
                Snackbar.make(h.toolbar, R.string.edit_not_found, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            is UiState.Ready -> bind(h, state)
        }
    }

    private fun bind(h: Holder, ready: UiState.Ready) {
        val d = ready.draft
        val addMode = viewModel.isAddMode()

        h.toolbar.setTitle(if (addMode) R.string.edit_add_title else R.string.edit_title)

        // Type control — only meaningful while adding; the type is immutable for existing rows.
        if (addMode) {
            h.typeToggle.visibility = View.VISIBLE
            renderTypeSegments(h, d.type)
        } else {
            h.typeToggle.visibility = View.GONE
        }

        // Payment mode segments
        renderPaymentSegments(h, d.paymentMode)

        // Currency pill + amount symbol
        val symbol = if (d.currency.equals("USD", ignoreCase = true)) "$" else "₹"
        h.currencyBtn.text = "$symbol ${d.currency.uppercase()}"
        h.amountSymbol.text = symbol

        // Text fields — set without re-firing the watcher.
        suppressTextWatchers = true
        if (h.amount.text?.toString() != d.amountText) h.amount.setText(d.amountText)
        if (h.merchant.text?.toString() != d.merchant) h.merchant.setText(d.merchant)
        if (h.notes.text?.toString() != d.notes) h.notes.setText(d.notes)
        suppressTextWatchers = false

        bindCategoryChips(h, ready.categories, d.categoryId)

        // Date button
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(d.occurredAt), zone)
        h.dateBtn.text = ldt.toLocalDate().format(dateFmt)

        // Source SMS — Edit mode only.
        if (d.smsBody.isNullOrBlank()) {
            h.smsSection.visibility = View.GONE
        } else {
            h.smsSection.visibility = View.VISIBLE
            h.smsBody.text = d.smsBody
        }
    }

    private fun renderTypeSegments(h: Holder, type: TransactionType) {
        setSegmentSelected(h.typeSpends, type == TransactionType.DEBIT)
        setSegmentSelected(h.typeCredits, type == TransactionType.CREDIT)
        setSegmentSelected(h.typeInvestments, type == TransactionType.INVESTMENT)
    }

    private fun setSegmentSelected(tv: TextView, on: Boolean) {
        tv.setBackgroundResource(if (on) R.drawable.bg_segment_on else 0)
        val attr = if (on) {
            com.google.android.material.R.attr.colorOnPrimaryContainer
        } else {
            com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        tv.setTextColor(MaterialColors.getColor(tv, attr))
    }

    /** Tapping the active mode clears it back to UNKNOWN; otherwise selects the tapped mode. */
    private fun onPaymentClick(mode: PaymentMode) {
        val current = (viewModel.state.value as? UiState.Ready)?.draft?.paymentMode
        viewModel.setPaymentMode(if (current == mode) PaymentMode.UNKNOWN else mode)
    }

    private fun renderPaymentSegments(h: Holder, mode: PaymentMode) {
        setPaymentSelected(h.pmUpi, mode == PaymentMode.UPI)
        setPaymentSelected(h.pmCredit, mode == PaymentMode.CARD_CREDIT)
        setPaymentSelected(h.pmDebit, mode == PaymentMode.CARD_DEBIT)
        setPaymentSelected(h.pmCash, mode == PaymentMode.CASH)
    }

    private fun setPaymentSelected(tv: TextView, on: Boolean) {
        tv.setBackgroundResource(if (on) R.drawable.bg_pm_box_on else R.drawable.bg_pm_box)
        val attr = if (on) {
            com.google.android.material.R.attr.colorOnSecondaryContainer
        } else {
            com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        val c = MaterialColors.getColor(tv, attr)
        tv.setTextColor(c)
        tv.compoundDrawableTintList = ColorStateList.valueOf(c)
    }

    private fun bindCategoryChips(h: Holder, categories: List<Category>, selectedId: Long) {
        val ids = categories.map { it.id }
        suppressListeners = true
        if (ids != renderedCategoryIds) {
            h.categoryChips.removeAllViews()
            for (cat in categories) {
                val chip = layoutInflater.inflate(R.layout.item_category_choice_chip, h.categoryChips, false) as Chip
                chip.id = View.generateViewId()
                chip.text = cat.name
                chip.tag = cat.id
                val color = runCatching { Color.parseColor(cat.colorHex) }.getOrElse { Color.GRAY }
                // Colour the leading dot, and outline the chip in its own colour only when selected.
                chip.isChipIconVisible = true
                chip.chipIconTint = ColorStateList.valueOf(color)
                chip.chipStrokeColor = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(color, Color.TRANSPARENT),
                )
                h.categoryChips.addView(chip)
            }
            renderedCategoryIds = ids
        }
        // Check the chip matching the draft's category.
        val target = (0 until h.categoryChips.childCount)
            .map { h.categoryChips.getChildAt(it) as Chip }
            .firstOrNull { (it.tag as? Long) == selectedId }
        if (target != null && !target.isChecked) target.isChecked = true
        suppressListeners = false
    }

    private fun showCurrencyMenu(anchor: View) {
        val currencies = listOf("INR", "USD")
        val menu = PopupMenu(requireContext(), anchor)
        currencies.forEachIndexed { i, c -> menu.menu.add(0, i, i, c) }
        menu.setOnMenuItemClickListener { item ->
            viewModel.setCurrency(currencies[item.itemId])
            true
        }
        menu.show()
    }

    private fun handleSaveResult(result: SaveResult) {
        when (result) {
            is SaveResult.InvalidAmount -> {
                binding?.let { Snackbar.make(it.saveBtn, R.string.edit_amount_invalid, Snackbar.LENGTH_SHORT).show() }
                viewModel.consumeSaveResult()
            }
            is SaveResult.Saved -> {
                viewModel.consumeSaveResult()
                findNavController().navigateUp()
            }
        }
    }

    private fun showDatePicker() {
        val current = (viewModel.state.value as? UiState.Ready)?.draft?.occurredAt ?: return
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(current)
            .setTitleText(R.string.edit_pick_date)
            .build()
        picker.addOnPositiveButtonClickListener { selectedUtcMidnight ->
            val pickedDate = Instant.ofEpochMilli(selectedUtcMidnight).atZone(ZoneId.of("UTC")).toLocalDate()
            val currentTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(current), zone).toLocalTime()
            val newMillis = pickedDate.atTime(currentTime).atZone(zone).toInstant().toEpochMilli()
            viewModel.setOccurredAt(newMillis)
        }
        picker.show(parentFragmentManager, "date")
    }

    private fun simpleWatcher(onChange: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChange(s?.toString().orEmpty())
    }

    companion object {
        const val ARG_TXN_ID = "txnId"
    }
}
