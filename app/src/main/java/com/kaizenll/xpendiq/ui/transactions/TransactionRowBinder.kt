package com.kaizenll.xpendiq.ui.transactions

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.util.CurrencyFormat
import com.kaizenll.xpendiq.util.DateFormat

/**
 * Renders an `item_transaction.xml` row from a [TransactionEntity] + optional [Category].
 * Shared by the Transactions list adapter, the Recent block on Home, and the Uncategorized
 * list, so row styling (avatar, tinted chip, color-coded amount) stays in one place.
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
        val timeView = view.findViewById<TextView>(R.id.time)
        val paymentModeView = view.findViewById<TextView>(R.id.payment_mode)
        val avatarView = view.findViewById<TextView>(R.id.avatar)
        val fxFlagView = view.findViewById<TextView>(R.id.fx_flag)

        val merchant = txn.merchantRaw?.takeIf { it.isNotBlank() } ?: "Unknown"
        merchantView.text = merchant
        // The day is already the section header; show only the clock time, and hide it when the
        // SMS had no time (midnight) so we never render a misleading "00:00".
        val time = DateFormat.timeOfDay(txn.occurredAt)
        timeView.text = time ?: ""
        timeView.visibility = if (time == null) View.GONE else View.VISIBLE
        paymentModeView.text = txn.paymentMode.name.replace('_', ' ')

        val categoryColor = category?.colorHex?.let { hex ->
            runCatching { Color.parseColor(hex) }.getOrNull()
        }

        bindCategoryChip(categoryView, category, categoryColor)
        bindAvatar(avatarView, merchant, categoryColor)
        bindAmount(amountView, txn)
        bindFxFlag(fxFlagView, txn)

        view.setOnClickListener { if (onClick != null) onClick(txn) }
    }

    private fun bindCategoryChip(categoryView: TextView, category: Category?, color: Int?) {
        categoryView.text = category?.name ?: "—"
        if (color != null) {
            categoryView.background = roundedFill(categoryView, 8f, (color and 0x00FFFFFF) or 0x33000000)
            categoryView.setTextColor(color)
        } else {
            categoryView.setBackgroundResource(R.drawable.bg_chip)
            categoryView.setTextColor(neutralOnVariant(categoryView))
        }
    }

    private fun bindAvatar(avatarView: TextView, merchant: String, color: Int?) {
        val initial = merchant.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
        avatarView.text = initial
        val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        if (color != null) {
            bg.setColor((color and 0x00FFFFFF) or 0x38000000) // ~22% alpha tint
            avatarView.setTextColor(color)
        } else {
            bg.setColor(neutralSurfaceVariant(avatarView))
            avatarView.setTextColor(neutralOnVariant(avatarView))
        }
        avatarView.background = bg
    }

    private fun bindAmount(amountView: TextView, txn: TransactionEntity) {
        val formatted = CurrencyFormat.format(txn.amountPaise, txn.currency)
        val ctx = amountView.context
        when (txn.type) {
            TransactionType.CREDIT -> {
                amountView.text = "+$formatted"
                amountView.setTextColor(ContextCompat.getColor(ctx, R.color.money_credit))
            }
            TransactionType.INVESTMENT -> {
                amountView.text = formatted
                amountView.setTextColor(ContextCompat.getColor(ctx, R.color.money_investment))
            }
            TransactionType.DEBIT -> {
                amountView.text = formatted
                amountView.setTextColor(neutralOnSurface(amountView))
            }
        }
    }

    private fun bindFxFlag(fxFlagView: TextView, txn: TransactionEntity) {
        if (txn.currency.equals("INR", ignoreCase = true)) {
            fxFlagView.visibility = View.GONE
        } else {
            fxFlagView.visibility = View.VISIBLE
            fxFlagView.text = txn.currency.uppercase()
        }
    }

    private fun roundedFill(view: View, cornerDp: Float, color: Int): GradientDrawable {
        val cornerPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, cornerDp, view.resources.displayMetrics,
        )
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            setColor(color)
        }
    }

    private fun neutralOnSurface(view: View): Int =
        MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)

    private fun neutralOnVariant(view: View): Int =
        MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurfaceVariant)

    private fun neutralSurfaceVariant(view: View): Int =
        MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceVariant)
}
