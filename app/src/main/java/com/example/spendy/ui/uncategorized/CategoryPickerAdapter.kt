package com.example.spendy.ui.uncategorized

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.spendy.R
import com.example.spendy.data.entity.Category

class CategoryPickerAdapter(
    private val onPick: (Category) -> Unit,
) : ListAdapter<PickerItem, RecyclerView.ViewHolder>(Diff) {

    init { setHasStableIds(true) }

    override fun getItemId(position: Int): Long = getItem(position).stableId.hashCode().toLong()

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is PickerItem.Header -> TYPE_HEADER
        is PickerItem.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(inflater.inflate(R.layout.item_category_section_header, parent, false))
            TYPE_ROW -> RowVH(inflater.inflate(R.layout.item_category_pick, parent, false), onPick)
            else -> error("unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is PickerItem.Header -> (holder as HeaderVH).bind(item)
            is PickerItem.Row -> (holder as RowVH).bind(item.category)
        }
    }

    private class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.title)
        fun bind(h: PickerItem.Header) { title.text = h.title }
    }

    private class RowVH(
        view: View,
        private val onPick: (Category) -> Unit,
    ) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.name)
        private val dot: View = view.findViewById(R.id.color_dot)

        fun bind(category: Category) {
            name.text = category.name
            val color = runCatching {
                android.graphics.Color.parseColor(category.colorHex)
            }.getOrElse { android.graphics.Color.GRAY }
            (dot.background as? GradientDrawable)?.setColor(color) ?: dot.setBackgroundColor(color)
            itemView.setOnClickListener { onPick(category) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<PickerItem>() {
        override fun areItemsTheSame(old: PickerItem, new: PickerItem): Boolean =
            old.stableId == new.stableId
        override fun areContentsTheSame(old: PickerItem, new: PickerItem): Boolean = old == new
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }
}
