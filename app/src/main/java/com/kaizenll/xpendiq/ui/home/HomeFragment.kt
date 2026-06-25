package com.kaizenll.xpendiq.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.ui.insights.CategoryBar
import com.kaizenll.xpendiq.ui.transactions.TransactionDetailSheet
import com.kaizenll.xpendiq.ui.transactions.TransactionListItem
import com.kaizenll.xpendiq.ui.transactions.TransactionRowBinder
import com.kaizenll.xpendiq.BuildConfig
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.entitlement.EntitlementState
import com.kaizenll.xpendiq.util.CurrencyFormat
import com.kaizenll.xpendiq.util.Motion
import com.kaizenll.xpendiq.util.NotificationAccess
import com.kaizenll.xpendiq.util.SmsPermissions
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
    private var monthTotalShown = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> refreshBanner() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.banner_grant).setOnClickListener {
            // Full flavor grants SMS at runtime; the notification-only flavor opens the system
            // notification-access screen (its capture path).
            if (BuildConfig.SMS_ENABLED) {
                permissionLauncher.launch(SmsPermissions.required)
            } else {
                NotificationAccess.openSettings(requireContext())
            }
        }

        view.findViewById<TextView>(R.id.month_pill).text = LocalDate.now().format(monthFmt)
        view.findViewById<TextView>(R.id.month_credited)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.money_credit))
        view.findViewById<TextView>(R.id.month_invested)
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.money_investment))

        val uncatCard = view.findViewById<MaterialCardView>(R.id.uncat_card)
        uncatCard.setOnClickListener {
            findNavController().navigate(R.id.uncategorizedFragment)
        }

        view.findViewById<MaterialCardView>(R.id.entitlement_banner).setOnClickListener {
            findNavController().navigate(R.id.paywallFragment)
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
                    val tv = view.findViewById<TextView>(R.id.month_total)
                    if (!monthTotalShown && v > 0L) {
                        monthTotalShown = true
                        Motion.countUpRupees(tv, v, whole = true)
                    } else {
                        tv.text = CurrencyFormat.paiseToInrWhole(v)
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.monthCredited.collect { v ->
                    view.findViewById<TextView>(R.id.month_credited).text = CurrencyFormat.paiseToInrOrDash(v)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.monthInvested.collect { v ->
                    view.findViewById<TextView>(R.id.month_invested).text = CurrencyFormat.paiseToInrOrDash(v)
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

        val inflater = LayoutInflater.from(container.context)
        for (c in cats) {
            val row = inflater.inflate(R.layout.item_category_legend, container, false)
            val color = runCatching { android.graphics.Color.parseColor(c.colorHex) }
                .getOrElse { android.graphics.Color.GRAY }
            val dot = row.findViewById<View>(R.id.color_dot)
            (dot.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.setColor(color)
                ?: dot.setBackgroundColor(color)
            row.findViewById<TextView>(R.id.name).text = c.name
            row.findViewById<TextView>(R.id.value).text = CurrencyFormat.paiseToInr(c.totalPaise)
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
        refreshEntitlementBanner()
    }

    /** Trial status / paywall teaser. Hidden when subscribed; shows the locked total when expired. */
    private fun refreshEntitlementBanner() {
        val view = view ?: return
        val app = requireContext().applicationContext as XpendiqApplication
        app.entitlement.refresh()
        val banner = view.findViewById<MaterialCardView>(R.id.entitlement_banner)
        val title = view.findViewById<TextView>(R.id.entitlement_banner_title)
        val subtitle = view.findViewById<TextView>(R.id.entitlement_banner_subtitle)
        when (val s = app.entitlement.state.value) {
            EntitlementState.Subscribed -> banner.visibility = View.GONE
            is EntitlementState.InTrial -> {
                banner.visibility = View.VISIBLE
                title.text = resources.getQuantityString(
                    R.plurals.entitlement_trial_title, s.daysLeft, s.daysLeft,
                )
                subtitle.text = getString(R.string.entitlement_trial_subtitle)
            }
            EntitlementState.Expired -> {
                banner.visibility = View.VISIBLE
                title.text = getString(R.string.entitlement_expired_title)
                viewLifecycleOwner.lifecycleScope.launch {
                    val count = app.repository.lockedCount()
                    subtitle.text = if (count > 0) {
                        val inr = CurrencyFormat.paiseToInr(app.repository.lockedSpendInrPaise())
                        resources.getQuantityString(
                            R.plurals.entitlement_locked_teaser, count, inr, count,
                        )
                    } else {
                        getString(R.string.entitlement_expired_readonly)
                    }
                }
            }
        }
    }

    private fun refreshBanner() {
        val view = view ?: return
        // The capture permission differs per flavor: SMS for full, notification access for play.
        val granted = if (BuildConfig.SMS_ENABLED) {
            SmsPermissions.smsGranted(requireContext())
        } else {
            NotificationAccess.isGranted(requireContext())
        }
        view.findViewById<MaterialCardView>(R.id.permission_banner).visibility =
            if (granted) View.GONE else View.VISIBLE
    }
}
