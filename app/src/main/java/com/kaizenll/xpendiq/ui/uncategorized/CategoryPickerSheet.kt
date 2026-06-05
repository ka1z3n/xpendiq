package com.kaizenll.xpendiq.ui.uncategorized

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.util.CurrencyFormat
import com.kaizenll.xpendiq.util.DateFormat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoryPickerSheet : BottomSheetDialogFragment() {

    private val viewModel: UncategorizedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.sheet_category_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bulkIds = requireArguments().getLongArray(ARG_BULK_IDS)
        if (bulkIds != null) {
            bindBulk(view, bulkIds)
            return
        }

        val txnId = requireArguments().getLong(ARG_TXN_ID)
        val app = requireActivity().application as XpendiqApplication

        viewLifecycleOwner.lifecycleScope.launch {
            val txn = withContext(Dispatchers.IO) { app.database.transactionDao().findById(txnId) }
            if (txn == null) { dismiss(); return@launch }
            bindHeader(view, txn)
            bindList(view, txn)
        }
    }

    /** Bulk mode: choose one category to apply to every selected transaction. */
    private fun bindBulk(view: View, ids: LongArray) {
        view.findViewById<TextView>(R.id.header_merchant).text =
            resources.getQuantityString(R.plurals.uncat_bulk_header, ids.size, ids.size)
        view.findViewById<TextView>(R.id.header_amount).setText(R.string.uncat_bulk_subtitle)
        view.findViewById<SwitchMaterial>(R.id.apply_all_switch).visibility = View.GONE
        view.findViewById<MaterialButton>(R.id.delete_btn).visibility = View.GONE

        val recycler = view.findViewById<RecyclerView>(R.id.categories)
        val adapter = CategoryPickerAdapter(onPick = { category ->
            viewModel.bulkRecategorize(ids.toList(), category)
            parentFragmentManager.setFragmentResult(RESULT_BULK_DONE, Bundle.EMPTY)
            dismiss()
        })
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pickableItems.collect { adapter.submitList(it) }
            }
        }
    }

    private fun bindHeader(view: View, txn: TransactionEntity) {
        val merchantText = txn.merchantRaw?.takeIf { it.isNotBlank() } ?: "Unknown"
        view.findViewById<TextView>(R.id.header_merchant).text = merchantText

        val mode = if (txn.paymentMode == PaymentMode.UNKNOWN) ""
        else " · ${txn.paymentMode.name.replace('_', ' ')}"
        view.findViewById<TextView>(R.id.header_amount).text =
            "${CurrencyFormat.format(txn.amountPaise, txn.currency)}$mode · ${DateFormat.row(txn.occurredAt)}"

        val switch = view.findViewById<SwitchMaterial>(R.id.apply_all_switch)
        if (txn.merchantNormalized.isNullOrBlank()) {
            switch.visibility = View.GONE
        } else {
            switch.text = getString(R.string.uncat_apply_all, merchantText)
        }

        view.findViewById<MaterialButton>(R.id.delete_btn).setOnClickListener {
            confirmDelete(txn)
        }
    }

    private fun confirmDelete(txn: TransactionEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.detail_delete_confirm_title)
            .setMessage(R.string.detail_delete_confirm_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.detail_delete) { _, _ ->
                viewModel.delete(txn)
                dismiss()
            }
            .show()
    }

    private fun bindList(view: View, txn: TransactionEntity) {
        val recycler = view.findViewById<RecyclerView>(R.id.categories)
        val switch = view.findViewById<SwitchMaterial>(R.id.apply_all_switch)
        val adapter = CategoryPickerAdapter(onPick = { category ->
            val applyAll = switch.isChecked && !txn.merchantNormalized.isNullOrBlank()
            viewModel.recategorize(txn, category, applyAll)
            dismiss()
        })
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pickableItems.collect { adapter.submitList(it) }
            }
        }
    }

    companion object {
        private const val ARG_TXN_ID = "txn_id"
        private const val ARG_BULK_IDS = "bulk_ids"

        /** FragmentResult key emitted after a bulk categorize is applied. */
        const val RESULT_BULK_DONE = "uncat_bulk_done"

        fun newInstance(txnId: Long): CategoryPickerSheet = CategoryPickerSheet().apply {
            arguments = Bundle().apply { putLong(ARG_TXN_ID, txnId) }
        }

        fun newInstanceBulk(ids: LongArray): CategoryPickerSheet = CategoryPickerSheet().apply {
            arguments = Bundle().apply { putLongArray(ARG_BULK_IDS, ids) }
        }
    }
}
