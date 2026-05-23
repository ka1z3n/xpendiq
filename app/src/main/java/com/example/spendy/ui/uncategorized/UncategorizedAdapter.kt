package com.example.spendy.ui.uncategorized

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.spendy.R
import com.example.spendy.data.entity.TransactionEntity
import com.example.spendy.util.CurrencyFormat
import com.example.spendy.util.DateFormat

class UncategorizedAdapter(
    private val onClick: (TransactionEntity) -> Unit,
) : ListAdapter<TransactionEntity, UncategorizedAdapter.VH>(Diff) {

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return VH(view, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(view: View, private val onClick: (TransactionEntity) -> Unit) : RecyclerView.ViewHolder(view) {
        private val merchant: TextView = view.findViewById(R.id.merchant)
        private val amount: TextView = view.findViewById(R.id.amount)
        private val category: TextView = view.findViewById(R.id.category)
        private val dateTime: TextView = view.findViewById(R.id.date_time)
        private val paymentMode: TextView = view.findViewById(R.id.payment_mode)

        fun bind(t: TransactionEntity) {
            merchant.text = t.merchantRaw?.takeIf { it.isNotBlank() } ?: "Unknown"
            amount.text = CurrencyFormat.format(t.amountPaise, t.currency)
            category.text = itemView.context.getString(R.string.uncat_chip)
            dateTime.text = DateFormat.row(t.occurredAt)
            paymentMode.text = t.paymentMode.name.replace('_', ' ')
            itemView.setOnClickListener { onClick(t) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<TransactionEntity>() {
        override fun areItemsTheSame(a: TransactionEntity, b: TransactionEntity) = a.id == b.id
        override fun areContentsTheSame(a: TransactionEntity, b: TransactionEntity) = a == b
    }
}
