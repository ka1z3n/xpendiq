package com.kaizenll.xpendiq.ui.insights

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.util.CurrencyFormat
import com.kaizenll.xpendiq.util.Motion
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
        view.findViewById<TextView>(R.id.mom_delta).text = formatDelta(ui)
        view.findViewById<TextView>(R.id.donut_total).text = CurrencyFormat.paiseToInrWhole(ui.totalSpent)

        view.findViewById<TextView>(R.id.credited).apply {
            text = CurrencyFormat.paiseToInrOrDash(ui.credited)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.money_credit))
        }
        view.findViewById<TextView>(R.id.invested).apply {
            text = CurrencyFormat.paiseToInrOrDash(ui.invested)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.money_investment))
        }

        bindPie(
            view.findViewById(R.id.pie),
            view.findViewById(R.id.legend_container),
            view.findViewById(R.id.bars_empty),
            view.findViewById(R.id.donut_center),
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
        donutCenter: View,
        bars: List<CategoryBar>,
    ) {
        legend.removeAllViews()
        if (bars.isEmpty()) {
            pie.setData(emptyList())
            pie.visibility = View.GONE
            donutCenter.visibility = View.GONE
            empty.visibility = View.VISIBLE
            return
        }
        pie.visibility = View.VISIBLE
        donutCenter.visibility = View.VISIBLE
        empty.visibility = View.GONE

        pie.setData(bars.map { parseColor(it.colorHex) to it.totalPaise }, animate = true)

        // Centre total fades up once the arcs have mostly drawn.
        if (Motion.enabled(requireContext())) {
            donutCenter.alpha = 0f
            donutCenter.animate().alpha(1f).setStartDelay(550L).setDuration(350L).start()
        } else {
            donutCenter.alpha = 1f
        }

        val total = bars.sumOf { it.totalPaise }.coerceAtLeast(1L)
        val inflater = LayoutInflater.from(legend.context)
        for ((i, b) in bars.withIndex()) {
            val row = inflater.inflate(R.layout.item_category_progress, legend, false)
            CategoryProgressBinder.bind(row, b.name, b.colorHex, b.totalPaise, total, animate = true, index = i)
            legend.addView(row)
        }
    }

    private fun parseColor(hex: String): Int = runCatching { Color.parseColor(hex) }.getOrElse { Color.GRAY }
}
