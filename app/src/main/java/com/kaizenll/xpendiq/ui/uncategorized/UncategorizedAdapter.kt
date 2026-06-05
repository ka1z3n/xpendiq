package com.kaizenll.xpendiq.ui.uncategorized

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.ui.transactions.TransactionRowBinder

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
        private val category: TextView = view.findViewById(R.id.category)

        fun bind(t: TransactionEntity) {
            // No category yet → shared binder renders a neutral avatar/chip; relabel the chip
            // to the explicit "Uncategorized" wording for this screen.
            TransactionRowBinder.bind(itemView, t, category = null, onClick = { onClick(t) })
            category.text = itemView.context.getString(R.string.uncat_chip)
        }
    }

    private object Diff : DiffUtil.ItemCallback<TransactionEntity>() {
        override fun areItemsTheSame(a: TransactionEntity, b: TransactionEntity) = a.id == b.id
        override fun areContentsTheSame(a: TransactionEntity, b: TransactionEntity) = a == b
    }
}
