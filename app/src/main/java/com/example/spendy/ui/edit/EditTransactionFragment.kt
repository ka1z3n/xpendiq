package com.example.spendy.ui.edit

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.spendy.R
import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionType
import com.example.spendy.ui.edit.EditTransactionViewModel.SaveResult
import com.example.spendy.ui.edit.EditTransactionViewModel.UiState
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class EditTransactionFragment : Fragment(R.layout.fragment_edit_transaction) {

    private val viewModel: EditTransactionViewModel by viewModels()
    private val zone = ZoneId.systemDefault()
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private var binding: Holder? = null
    private var suppressTextWatchers = false

    private class Holder(view: View) {
        val toolbar: MaterialToolbar = view.findViewById(R.id.toolbar)
        val typeLayout: TextInputLayout = view.findViewById(R.id.type_layout)
        val type: MaterialAutoCompleteTextView = view.findViewById(R.id.type)
        val amountLayout: TextInputLayout = view.findViewById(R.id.amount_layout)
        val amount: TextInputEditText = view.findViewById(R.id.amount)
        val currency: MaterialAutoCompleteTextView = view.findViewById(R.id.currency)
        val merchant: TextInputEditText = view.findViewById(R.id.merchant)
        val category: MaterialAutoCompleteTextView = view.findViewById(R.id.category)
        val paymentMode: MaterialAutoCompleteTextView = view.findViewById(R.id.payment_mode)
        val dateBtn: MaterialButton = view.findViewById(R.id.date_btn)
        val timeBtn: MaterialButton = view.findViewById(R.id.time_btn)
        val notes: TextInputEditText = view.findViewById(R.id.notes)
        val smsSection: LinearLayout = view.findViewById(R.id.sms_section)
        val smsBody: TextView = view.findViewById(R.id.sms_body)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val txnId = arguments?.getLong(ARG_TXN_ID, EditTransactionViewModel.TXN_ID_NEW)
            ?: EditTransactionViewModel.TXN_ID_NEW
        viewModel.load(txnId)

        val h = Holder(view).also { binding = it }

        h.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        h.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_save) { viewModel.save(); true } else false
        }

        h.amount.addTextChangedListener(simpleWatcher { if (!suppressTextWatchers) viewModel.setAmount(it) })
        h.merchant.addTextChangedListener(simpleWatcher { if (!suppressTextWatchers) viewModel.setMerchant(it) })
        h.notes.addTextChangedListener(simpleWatcher { if (!suppressTextWatchers) viewModel.setNotes(it) })

        h.dateBtn.setOnClickListener { showDatePicker() }
        h.timeBtn.setOnClickListener { showTimePicker() }

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

        // Toolbar title.
        h.toolbar.setTitle(if (addMode) R.string.edit_add_title else R.string.edit_title)

        // Type dropdown is only shown in Add mode; the type is immutable for existing rows.
        if (addMode) {
            h.typeLayout.visibility = View.VISIBLE
            val types = listOf(TransactionType.DEBIT, TransactionType.CREDIT, TransactionType.INVESTMENT)
            val labels = types.map { typeLabel(it) }
            h.type.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
            h.type.setText(typeLabel(d.type), false)
            h.type.setOnItemClickListener { _, _, position, _ -> viewModel.setType(types[position]) }
        } else {
            h.typeLayout.visibility = View.GONE
        }

        // Currency dropdown.
        val currencies = listOf("INR", "USD")
        h.currency.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, currencies))
        h.currency.setText(d.currency, false)
        h.currency.setOnItemClickListener { _, _, position, _ ->
            viewModel.setCurrency(currencies[position])
        }
        h.amountLayout.prefixText = when (d.currency.uppercase()) {
            "USD" -> "$"
            else -> "₹"
        }

        // Text fields — set without re-firing the watcher.
        suppressTextWatchers = true
        if (h.amount.text?.toString() != d.amountText) h.amount.setText(d.amountText)
        if (h.merchant.text?.toString() != d.merchant) h.merchant.setText(d.merchant)
        if (h.notes.text?.toString() != d.notes) h.notes.setText(d.notes)
        suppressTextWatchers = false

        // Category dropdown
        val catNames = ready.categories.map { it.name }
        h.category.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, catNames))
        val currentCat = ready.categories.firstOrNull { it.id == d.categoryId }
        h.category.setText(currentCat?.name.orEmpty(), false)
        h.category.setOnItemClickListener { _, _, position, _ ->
            viewModel.setCategoryId(ready.categories[position].id)
        }

        // Payment mode dropdown
        val modes = PaymentMode.values().toList()
        val modeLabels = modes.map { it.name.replace('_', ' ') }
        h.paymentMode.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, modeLabels))
        h.paymentMode.setText(d.paymentMode.name.replace('_', ' '), false)
        h.paymentMode.setOnItemClickListener { _, _, position, _ ->
            viewModel.setPaymentMode(modes[position])
        }

        // Date/time buttons
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(d.occurredAt), zone)
        h.dateBtn.text = ldt.toLocalDate().format(dateFmt)
        h.timeBtn.text = ldt.toLocalTime().format(timeFmt)

        // SMS body — only visible in Edit mode (Add mode has no source SMS).
        if (d.smsBody.isNullOrBlank()) {
            h.smsSection.visibility = View.GONE
        } else {
            h.smsSection.visibility = View.VISIBLE
            h.smsBody.text = d.smsBody
        }
    }

    private fun typeLabel(type: TransactionType): String = when (type) {
        TransactionType.DEBIT -> getString(R.string.tab_spends)
        TransactionType.CREDIT -> getString(R.string.tab_credits)
        TransactionType.INVESTMENT -> getString(R.string.investments_title)
    }

    private fun handleSaveResult(result: SaveResult) {
        when (result) {
            is SaveResult.InvalidAmount -> {
                binding?.amountLayout?.error = getString(R.string.edit_amount_invalid)
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

    private fun showTimePicker() {
        val current = (viewModel.state.value as? UiState.Ready)?.draft?.occurredAt ?: return
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(current), zone)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(ldt.hour)
            .setMinute(ldt.minute)
            .setTitleText(R.string.edit_pick_time)
            .build()
        picker.addOnPositiveButtonClickListener {
            val newLdt = ldt.toLocalDate().atTime(picker.hour, picker.minute)
            viewModel.setOccurredAt(newLdt.atZone(zone).toInstant().toEpochMilli())
        }
        picker.show(parentFragmentManager, "time")
    }

    private fun simpleWatcher(onChange: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            onChange(s?.toString().orEmpty())
            binding?.amountLayout?.error = null
        }
    }

    companion object {
        const val ARG_TXN_ID = "txnId"
    }
}
