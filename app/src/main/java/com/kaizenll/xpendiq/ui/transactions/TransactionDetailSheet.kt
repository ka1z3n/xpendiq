package com.kaizenll.xpendiq.ui.transactions

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.util.CurrencyFormat
import com.kaizenll.xpendiq.util.DateFormat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TransactionDetailSheet : BottomSheetDialogFragment() {

    private val viewModel: TransactionsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.sheet_transaction_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val txnId = requireArguments().getLong(ARG_TXN_ID)
        val app = requireActivity().application as XpendiqApplication

        // Fetch the txn + category on IO, then bind.
        CoroutineScope(Dispatchers.Main).launch {
            val pair = fetch(app, txnId)
            if (pair == null) { dismiss(); return@launch }
            val (txn, categoryName) = pair
            bind(view, txn, categoryName)
        }
    }

    private suspend fun fetch(app: XpendiqApplication, txnId: Long): Pair<TransactionEntity, String?>? {
        val txn = withDb { app.database.transactionDao().findById(txnId) } ?: return null
        val category = withDb { app.database.categoryDao().findById(txn.categoryId) }
        return txn to category?.name
    }

    private suspend fun <T> withDb(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }

    private fun bind(view: View, txn: TransactionEntity, categoryName: String?) {
        view.findViewById<TextView>(R.id.amount).text = CurrencyFormat.format(txn.amountPaise, txn.currency)
        view.findViewById<TextView>(R.id.merchant).text = txn.merchantRaw?.takeIf { it.isNotBlank() } ?: "Unknown"

        val mode = if (txn.paymentMode == PaymentMode.UNKNOWN) "" else " · ${txn.paymentMode.name.replace('_', ' ')}"
        view.findViewById<TextView>(R.id.meta).text =
            "${categoryName ?: "—"}$mode · ${DateFormat.row(txn.occurredAt)}"

        val accountView = view.findViewById<TextView>(R.id.account)
        if (!txn.accountTail.isNullOrBlank()) {
            accountView.text = "A/c ending ${txn.accountTail}"
            accountView.visibility = View.VISIBLE
        } else {
            accountView.visibility = View.GONE
        }

        // The original SMS is hidden behind a "See original message" link — most users just want
        // the parsed summary, and the raw text is reassurance-on-demand. No stored body, no link.
        val seeOriginal = view.findViewById<TextView>(R.id.see_original_link)
        val smsSection = view.findViewById<View>(R.id.sms_section)
        val smsBody = txn.smsBody
        if (smsBody.isNullOrBlank()) {
            seeOriginal.visibility = View.GONE
            smsSection.visibility = View.GONE
        } else {
            view.findViewById<TextView>(R.id.sms_body).text = smsBody
            seeOriginal.visibility = View.VISIBLE
            seeOriginal.setOnClickListener {
                seeOriginal.visibility = View.GONE
                smsSection.visibility = View.VISIBLE
            }
        }

        view.findViewById<MaterialButton>(R.id.delete_btn).setOnClickListener {
            confirmDelete(txn)
        }

        view.findViewById<MaterialButton>(R.id.edit_btn).setOnClickListener {
            val controller = Navigation.findNavController(requireActivity(), R.id.nav_host)
            controller.navigate(
                R.id.editTransactionFragment,
                bundleOf("txnId" to txn.id),
                EDIT_NAV_OPTIONS,
            )
            dismiss()
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

    companion object {
        private const val ARG_TXN_ID = "txn_id"
        fun newInstance(txnId: Long): TransactionDetailSheet = TransactionDetailSheet().apply {
            arguments = Bundle().apply { putLong(ARG_TXN_ID, txnId) }
        }
    }
}
