package com.example.spendy.ui.insights

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.spendy.R
import com.example.spendy.util.CurrencyFormat
import com.google.android.material.button.MaterialButton
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class InsightsFragment : Fragment(R.layout.fragment_insights) {

    private val viewModel: InsightsViewModel by viewModels()
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val shortMonthFmt = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prevBtn = view.findViewById<MaterialButton>(R.id.prev_btn)
        val nextBtn = view.findViewById<MaterialButton>(R.id.next_btn)
        prevBtn.setOnClickListener { viewModel.prevMonth() }
        nextBtn.setOnClickListener { viewModel.nextMonth() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { renderState(view, it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.canGoNext.collect { nextBtn.isEnabled = it }
            }
        }
    }

    private fun renderState(view: View, ui: InsightsUi) {
        view.findViewById<TextView>(R.id.month_label).text = ui.month.format(monthFmt)
        view.findViewById<TextView>(R.id.total_spent).text = CurrencyFormat.paiseToInr(ui.totalSpent)
        view.findViewById<TextView>(R.id.mom_delta).text = formatDelta(ui)
        view.findViewById<TextView>(R.id.credited).text = CurrencyFormat.paiseToInr(ui.credited)
        view.findViewById<TextView>(R.id.invested).text = CurrencyFormat.paiseToInr(ui.invested)

        bindPie(
            view.findViewById(R.id.pie),
            view.findViewById(R.id.legend_container),
            view.findViewById(R.id.bars_empty),
            ui.bars,
        )
    }

    private fun formatDelta(ui: InsightsUi): String {
        val prev = ui.previousTotal
        val curr = ui.totalSpent
        val prevMonth = ui.month.minusMonths(1).format(shortMonthFmt)
        if (prev <= 0L) return getString(R.string.insights_no_prev_compare, prevMonth)
        val diff = curr - prev
        val pct = (diff.toDouble() / prev.toDouble()) * 100.0
        val arrow = when {
            diff > 0 -> "▲"
            diff < 0 -> "▼"
            else -> "="
        }
        return getString(R.string.insights_mom, arrow, kotlin.math.abs(pct).toInt(), prevMonth)
    }

    private fun bindPie(
        pie: CategoryPieView,
        legend: LinearLayout,
        empty: TextView,
        bars: List<CategoryBar>,
    ) {
        legend.removeAllViews()
        if (bars.isEmpty()) {
            pie.setData(emptyList())
            pie.visibility = View.GONE
            empty.visibility = View.VISIBLE
            return
        }
        pie.visibility = View.VISIBLE
        empty.visibility = View.GONE

        pie.setData(bars.map { parseColor(it.colorHex) to it.totalPaise })

        val total = bars.sumOf { it.totalPaise }.coerceAtLeast(1L)
        val inflater = LayoutInflater.from(legend.context)
        for (b in bars) {
            val row = inflater.inflate(R.layout.item_category_legend, legend, false)
            val color = parseColor(b.colorHex)
            val dot = row.findViewById<View>(R.id.color_dot)
            (dot.background as? GradientDrawable)?.setColor(color) ?: dot.setBackgroundColor(color)
            row.findViewById<TextView>(R.id.name).text = b.name
            row.findViewById<TextView>(R.id.amount).text = CurrencyFormat.paiseToInr(b.totalPaise)
            val pct = ((b.totalPaise.toDouble() / total.toDouble()) * 100.0).toInt()
            row.findViewById<TextView>(R.id.percent).text = "$pct%"
            legend.addView(row)
        }
    }

    private fun parseColor(hex: String): Int = runCatching { Color.parseColor(hex) }.getOrElse { Color.GRAY }
}
