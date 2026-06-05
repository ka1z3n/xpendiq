package com.kaizenll.xpendiq.ui.uncategorized

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.ui.transactions.TransactionRowBinder

/**
 * Uncategorized list with multi-select. Long-press a row to enter selection mode, then tap
 * rows to toggle them; [onSelectionChanged] lets the host drive a contextual toolbar.
 */
class UncategorizedAdapter(
    private val onClick: (TransactionEntity) -> Unit,
    private val onSelectionChanged: () -> Unit,
) : ListAdapter<TransactionEntity, UncategorizedAdapter.VH>(Diff) {

    var selectionMode = false
        private set
    private val selected = LinkedHashSet<Long>()

    val selectedCount: Int get() = selected.size
    fun selectedIds(): LongArray = selected.toLongArray()

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    private fun enterSelection(id: Long) {
        selectionMode = true
        selected.add(id)
        notifyDataSetChanged()
        onSelectionChanged()
    }

    private fun toggle(id: Long, position: Int) {
        if (!selected.remove(id)) selected.add(id)
        if (selected.isEmpty()) {
            clearSelection()
        } else {
            if (position != RecyclerView.NO_POSITION) notifyItemChanged(position)
            onSelectionChanged()
        }
    }

    fun clearSelection() {
        if (!selectionMode && selected.isEmpty()) return
        selectionMode = false
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val category: TextView = view.findViewById(R.id.category)
        private val avatar: TextView = view.findViewById(R.id.avatar)

        fun bind(t: TransactionEntity) {
            // No category yet → shared binder renders a neutral avatar/chip; relabel the chip
            // to the explicit "Uncategorized" wording for this screen.
            TransactionRowBinder.bind(itemView, t, category = null)
            category.text = itemView.context.getString(R.string.uncat_chip)

            applySelectionVisual(selectionMode && selected.contains(t.id))

            itemView.setOnClickListener {
                if (selectionMode) toggle(t.id, bindingAdapterPosition) else onClick(t)
            }
            itemView.setOnLongClickListener {
                if (selectionMode) toggle(t.id, bindingAdapterPosition) else enterSelection(t.id)
                true
            }
        }

        private fun applySelectionVisual(isSelected: Boolean) {
            if (isSelected) {
                val primary = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimary)
                itemView.setBackgroundColor(ColorUtils.setAlphaComponent(primary, 0x1F))
                val container = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimaryContainer)
                val onContainer = MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnPrimaryContainer)
                avatar.text = "✓"
                avatar.setTextColor(onContainer)
                avatar.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(container)
                }
            } else {
                itemView.setBackgroundResource(selectableItemBackground(itemView))
                // avatar already restored by TransactionRowBinder.bind above
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<TransactionEntity>() {
        override fun areItemsTheSame(a: TransactionEntity, b: TransactionEntity) = a.id == b.id
        override fun areContentsTheSame(a: TransactionEntity, b: TransactionEntity) = a == b
    }

    private companion object {
        private var cachedSelectableBg = 0
        fun selectableItemBackground(view: View): Int {
            if (cachedSelectableBg == 0) {
                val tv = TypedValue()
                view.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
                cachedSelectableBg = tv.resourceId
            }
            return cachedSelectableBg
        }
    }
}
