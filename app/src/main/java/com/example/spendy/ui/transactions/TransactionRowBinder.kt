package com.example.spendy.ui.transactions

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.example.spendy.R
import com.example.spendy.data.entity.Category
import com.example.spendy.data.entity.TransactionEntity
import com.example.spendy.util.CurrencyFormat
import com.example.spendy.util.DateFormat

/**
 * Renders an `item_transaction.xml` row from a [TransactionEntity] + optional [Category].
 * Shared by the Transactions list adapter and the Recent-transactions block on Home so the
 * row styling (tinted category chip etc.) stays in one place.
 */
object TransactionRowBinder {

    fun bind(
        view: View,
        txn: TransactionEntity,
        category: Category?,
        onClick: ((TransactionEntity) -> Unit)? = null,
    ) {
        val merchantView = view.findViewById<TextView>(R.id.merchant)
        val amountView = view.findViewById<TextView>(R.id.amount)
        val categoryView = view.findViewById<TextView>(R.id.category)
        val dateTimeView = view.findViewById<TextView>(R.id.date_time)
        val paymentModeView = view.findViewById<TextView>(R.id.payment_mode)

        merchantView.text = txn.merchantRaw?.takeIf { it.isNotBlank() } ?: "Unknown"
        amountView.text = CurrencyFormat.format(txn.amountPaise, txn.currency)
        categoryView.text = category?.name ?: "—"
        dateTimeView.text = DateFormat.row(txn.occurredAt)
        paymentModeView.text = txn.paymentMode.name.replace('_', ' ')

        val colorHex = category?.colorHex
        if (colorHex != null) {
            val color = runCatching { Color.parseColor(colorHex) }.getOrElse { Color.GRAY }
            val cornerPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, view.resources.displayMetrics,
            )
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerPx
                setColor((color and 0x00FFFFFF) or 0x33000000)  // 20% alpha
            }
            categoryView.background = bg
            categoryView.setTextColor(color)
        } else {
            categoryView.setBackgroundResource(R.drawable.bg_chip)
        }

        view.setOnClickListener { if (onClick != null) onClick(txn) }
    }
}
