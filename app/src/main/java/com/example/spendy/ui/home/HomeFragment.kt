package com.example.spendy.ui.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.spendy.R
import com.example.spendy.ui.insights.CategoryBar
import com.example.spendy.ui.transactions.TransactionDetailSheet
import com.example.spendy.ui.transactions.TransactionListItem
import com.example.spendy.ui.transactions.TransactionRowBinder
import com.example.spendy.util.CurrencyFormat
import com.example.spendy.util.SmsPermissions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> refreshBanner() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.banner_grant).setOnClickListener {
            permissionLauncher.launch(SmsPermissions.required)
        }

        val uncatCard = view.findViewById<MaterialCardView>(R.id.uncat_card)
        uncatCard.setOnClickListener {
            findNavController().navigate(R.id.uncategorizedFragment)
        }

        view.findViewById<MaterialButton>(R.id.top_categories_see_all).setOnClickListener {
            findNavController().navigate(R.id.insightsFragment)
        }
        view.findViewById<MaterialButton>(R.id.recent_see_all).setOnClickListener {
            findNavController().navigate(R.id.transactionsFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.monthSpend.collect { v ->
                    view.findViewById<TextView>(R.id.month_total).text = CurrencyFormat.paiseToInr(v)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.monthCredited.collect { v ->
                    view.findViewById<TextView>(R.id.month_credited).text = CurrencyFormat.paiseToInr(v)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.monthInvested.collect { v ->
                    view.findViewById<TextView>(R.id.month_invested).text = CurrencyFormat.paiseToInr(v)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uncategorizedCount.collect { count ->
                    if (count > 0) {
                        uncatCard.visibility = View.VISIBLE
                        view.findViewById<TextView>(R.id.uncat_count).text =
                            resources.getQuantityString(R.plurals.home_uncat_count, count, count)
                    } else {
                        uncatCard.visibility = View.GONE
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.topSpendCategories.collect { cats -> renderTopCategories(view, cats) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentDebits.collect { rows -> renderRecent(view, rows) }
            }
        }
    }

    private fun renderTopCategories(root: View, cats: List<CategoryBar>) {
        val card = root.findViewById<MaterialCardView>(R.id.top_categories_card)
        val container = root.findViewById<LinearLayout>(R.id.top_categories_container)
        container.removeAllViews()
        if (cats.isEmpty()) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE

        val total = cats.sumOf { it.totalPaise }.coerceAtLeast(1L)
        val inflater = LayoutInflater.from(container.context)
        for (c in cats) {
            val row = inflater.inflate(R.layout.item_category_legend, container, false)
            val color = runCatching { Color.parseColor(c.colorHex) }.getOrElse { Color.GRAY }
            val dot = row.findViewById<View>(R.id.color_dot)
            (dot.background as? GradientDrawable)?.setColor(color) ?: dot.setBackgroundColor(color)
            row.findViewById<TextView>(R.id.name).text = c.name
            row.findViewById<TextView>(R.id.amount).text = CurrencyFormat.paiseToInr(c.totalPaise)
            val pct = ((c.totalPaise.toDouble() / total.toDouble()) * 100.0).toInt()
            row.findViewById<TextView>(R.id.percent).text = "$pct%"
            container.addView(row)
        }
    }

    private fun renderRecent(root: View, rows: List<TransactionListItem.Row>) {
        val card = root.findViewById<MaterialCardView>(R.id.recent_card)
        val container = root.findViewById<LinearLayout>(R.id.recent_container)
        container.removeAllViews()
        if (rows.isEmpty()) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(container.context)
        for (row in rows) {
            val rowView = inflater.inflate(R.layout.item_transaction, container, false)
            TransactionRowBinder.bind(
                view = rowView,
                txn = row.txn,
                category = row.category,
                onClick = { txn ->
                    TransactionDetailSheet.newInstance(txn.id)
                        .show(parentFragmentManager, "txn_detail")
                },
            )
            container.addView(rowView)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBanner()
    }

    private fun refreshBanner() {
        val view = view ?: return
        val granted = SmsPermissions.smsGranted(requireContext())
        view.findViewById<MaterialCardView>(R.id.permission_banner).visibility =
            if (granted) View.GONE else View.VISIBLE
    }
}
