package com.kaizenll.xpendiq.ui.categories

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.util.CurrencyFormat

class CategoriesAdapter(
    private val onRowClick: (CategoriesListItem.Row) -> Unit,
    private val onRowLongClick: (CategoriesListItem.Row) -> Unit,
) : ListAdapter<CategoriesListItem, RecyclerView.ViewHolder>(Diff) {

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).stableId.hashCode().toLong()

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is CategoriesListItem.Header -> TYPE_HEADER
        is CategoriesListItem.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(inflater.inflate(R.layout.item_category_section_header, parent, false))
            TYPE_ROW -> RowVH(
                inflater.inflate(R.layout.item_category_manage_row, parent, false),
                onRowClick,
                onRowLongClick,
            )
            else -> error("unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is CategoriesListItem.Header -> (holder as HeaderVH).bind(item)
            is CategoriesListItem.Row -> (holder as RowVH).bind(item)
        }
    }

    private class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.title)
        fun bind(h: CategoriesListItem.Header) { title.text = h.title }
    }

    private class RowVH(
        view: View,
        private val onClick: (CategoriesListItem.Row) -> Unit,
        private val onLongClick: (CategoriesListItem.Row) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val dot: View = view.findViewById(R.id.color_dot)
        private val name: TextView = view.findViewById(R.id.name)
        private val systemBadge: TextView = view.findViewById(R.id.system_badge)
        private val amount: TextView = view.findViewById(R.id.amount)

        fun bind(row: CategoriesListItem.Row) {
            name.text = row.category.name
            amount.text = CurrencyFormat.paiseToInr(row.monthlyPaise)
            systemBadge.visibility = if (row.category.isSystem) View.VISIBLE else View.GONE

            val color = runCatching { Color.parseColor(row.category.colorHex) }.getOrElse { Color.GRAY }
            (dot.background as? GradientDrawable)?.setColor(color) ?: dot.setBackgroundColor(color)

            itemView.setOnClickListener { onClick(row) }
            itemView.setOnLongClickListener { onLongClick(row); true }
        }
    }

    private object Diff : DiffUtil.ItemCallback<CategoriesListItem>() {
        override fun areItemsTheSame(old: CategoriesListItem, new: CategoriesListItem) =
            old.stableId == new.stableId
        override fun areContentsTheSame(old: CategoriesListItem, new: CategoriesListItem) = old == new
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }
}
