package com.kaizenll.xpendiq.ui.insights

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
        view.findViewById<TextView>(R.id.donut_total).text = CurrencyFormat.paiseToInrWhole(ui.totalSpent)

        bindDelta(view, ui)
        view.findViewById<TextView>(R.id.daily_avg).text =
            CurrencyFormat.paiseToInrOrDash(ui.dailyAverage)

        bindPie(
            view.findViewById(R.id.pie),
            view.findViewById(R.id.legend_container),
            view.findViewById(R.id.bars_empty),
            view.findViewById(R.id.donut_center),
            ui.bars,
        )
    }

    /** "Vs May" label + a coloured ▲/▼ delta. Down on spending reads green, up reads red. */
    private fun bindDelta(view: View, ui: InsightsUi) {
        val prevMonth = ui.month.minusMonths(1).format(shortMonthFmt)
        view.findViewById<TextView>(R.id.vs_label).text =
            getString(R.string.insights_vs_month, prevMonth)

        val valueView = view.findViewById<TextView>(R.id.vs_value)
        val prev = ui.previousTotal
        if (prev <= 0L) {
            valueView.text = "—"
            valueView.setTextColor(neutralColor())
            return
        }
        val diff = ui.totalSpent - prev
        val pct = kotlin.math.abs((diff.toDouble() / prev.toDouble()) * 100.0).toInt()
        when {
            diff > 0 -> {
                valueView.text = getString(R.string.insights_delta_pct, "▲", pct)
                valueView.setTextColor(themeColor(com.google.android.material.R.attr.colorError))
            }
            diff < 0 -> {
                valueView.text = getString(R.string.insights_delta_pct, "▼", pct)
                valueView.setTextColor(ContextCompat.getColor(requireContext(), R.color.money_credit))
            }
            else -> {
                valueView.text = getString(R.string.insights_delta_pct, "=", pct)
                valueView.setTextColor(neutralColor())
            }
        }
    }

    private fun neutralColor(): Int = themeColor(com.google.android.material.R.attr.colorOnSurface)

    private fun themeColor(@androidx.annotation.AttrRes attr: Int): Int =
        com.google.android.material.color.MaterialColors.getColor(requireView(), attr)

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
        for (b in bars) {
            val row = inflater.inflate(R.layout.item_category_legend, legend, false)
            bindLegendRow(row, b, total)
            legend.addView(row)
        }
    }

    private fun bindLegendRow(row: View, bar: CategoryBar, total: Long) {
        val color = parseColor(bar.colorHex)
        val dot = row.findViewById<View>(R.id.color_dot)
        (dot.background?.mutate() as? GradientDrawable)?.setColor(color) ?: dot.setBackgroundColor(color)

        row.findViewById<TextView>(R.id.name).text = bar.name

        val pct = ((bar.totalPaise.toDouble() / total.toDouble()) * 100.0).toInt()
        val pctText = if (pct == 0 && bar.totalPaise > 0L) getString(R.string.pct_lt_one) else "$pct%"
        row.findViewById<TextView>(R.id.value).text =
            "${CurrencyFormat.paiseToInr(bar.totalPaise)} · $pctText"
    }

    private fun parseColor(hex: String): Int = runCatching { Color.parseColor(hex) }.getOrElse { Color.GRAY }
}
