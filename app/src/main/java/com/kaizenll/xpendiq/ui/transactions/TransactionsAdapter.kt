package com.kaizenll.xpendiq.ui.transactions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.util.CurrencyFormat

/** Renders one filled card ([DaySection]) per calendar day, inflating its rows inline. */
class TransactionsAdapter(
    private val onRowClick: (TransactionListItem.Row) -> Unit,
) : ListAdapter<DaySection, TransactionsAdapter.DayVH>(Diff) {

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).dayKey

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_day_section, parent, false)
        return DayVH(view, onRowClick)
    }

    override fun onBindViewHolder(holder: DayVH, position: Int) = holder.bind(getItem(position))

    class DayVH(
        view: View,
        private val onRowClick: (TransactionListItem.Row) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.date_label)
        private val total: TextView = view.findViewById(R.id.date_total)
        private val container: LinearLayout = view.findViewById(R.id.rows_container)
        private val inflater = LayoutInflater.from(view.context)

        fun bind(section: DaySection) {
            label.text = section.label
            total.text = CurrencyFormat.paiseToInr(section.totalAmountPaise)
            container.removeAllViews()
            for (row in section.rows) {
                val rowView = inflater.inflate(R.layout.item_transaction, container, false)
                TransactionRowBinder.bind(rowView, row.txn, row.category, onClick = { onRowClick(row) })
                container.addView(rowView)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DaySection>() {
        override fun areItemsTheSame(old: DaySection, new: DaySection): Boolean =
            old.dayKey == new.dayKey

        override fun areContentsTheSame(old: DaySection, new: DaySection): Boolean =
            old == new
    }
}
