package com.kaizenll.xpendiq.ui.insights

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.util.CurrencyFormat

/**
 * Binds an `item_category_progress.xml` row: colored dot, name, amount, percent, and a tinted
 * progress bar sized to the category's share of [totalPaise]. Shared by Home (top categories)
 * and Insights (legend) so the styling lives in one place.
 */
object CategoryProgressBinder {

    fun bind(row: View, name: String, colorHex: String, amountPaise: Long, totalPaise: Long) {
        val color = runCatching { Color.parseColor(colorHex) }.getOrElse { Color.GRAY }

        val dot = row.findViewById<View>(R.id.color_dot)
        (dot.background as? GradientDrawable)?.setColor(color) ?: dot.setBackgroundColor(color)

        row.findViewById<TextView>(R.id.name).text = name
        row.findViewById<TextView>(R.id.amount).text = CurrencyFormat.paiseToInr(amountPaise)

        val total = totalPaise.coerceAtLeast(1L)
        val fraction = (amountPaise.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
        val pct = (fraction * 100.0).toInt()
        val percentView = row.findViewById<TextView>(R.id.percent)
        percentView.text = if (pct == 0 && amountPaise > 0L) {
            percentView.context.getString(R.string.pct_lt_one)
        } else {
            "$pct%"
        }

        val bar = row.findViewById<View>(R.id.bar)
        val spacer = row.findViewById<View>(R.id.spacer)
        (bar.layoutParams as LinearLayout.LayoutParams).weight = fraction
        (spacer.layoutParams as LinearLayout.LayoutParams).weight = 1f - fraction
        bar.requestLayout()

        val cornerPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 4f, row.resources.displayMetrics,
        )
        bar.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            setColor(color)
        }
    }
}
