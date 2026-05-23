package com.example.spendy.ui.transactions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.spendy.R
import com.example.spendy.util.CurrencyFormat

class TransactionsAdapter(
    private val onRowClick: (TransactionListItem.Row) -> Unit,
) : ListAdapter<TransactionListItem, RecyclerView.ViewHolder>(Diff) {

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).stableId.hashCode().toLong()

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TransactionListItem.Header -> TYPE_HEADER
        is TransactionListItem.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(inflater.inflate(R.layout.item_date_header, parent, false))
            TYPE_ROW -> RowVH(inflater.inflate(R.layout.item_transaction, parent, false), onRowClick)
            else -> error("unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TransactionListItem.Header -> (holder as HeaderVH).bind(item)
            is TransactionListItem.Row -> (holder as RowVH).bind(item)
        }
    }

    private class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.date_label)
        private val total: TextView = view.findViewById(R.id.date_total)
        fun bind(h: TransactionListItem.Header) {
            label.text = h.label
            total.text = CurrencyFormat.paiseToInr(h.totalAmountPaise)
        }
    }

    private class RowVH(
        view: View,
        private val onClick: (TransactionListItem.Row) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        fun bind(row: TransactionListItem.Row) {
            TransactionRowBinder.bind(itemView, row.txn, row.category, onClick = { _ -> onClick(row) })
        }
    }

    private object Diff : DiffUtil.ItemCallback<TransactionListItem>() {
        override fun areItemsTheSame(old: TransactionListItem, new: TransactionListItem): Boolean =
            old.stableId == new.stableId

        override fun areContentsTheSame(old: TransactionListItem, new: TransactionListItem): Boolean =
            old == new
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }
}
