package com.kaizenll.xpendiq.ui.paywall

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.kaizenll.xpendiq.BuildConfig
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.entitlement.EntitlementState
import com.kaizenll.xpendiq.util.CurrencyFormat
import com.kaizenll.xpendiq.util.Preferences
import kotlinx.coroutines.launch

/**
 * Subscription screen — also the "what you tracked" trial summary. Reached from the Home trial
 * banner and the "subscribe to edit" prompts. Subscribe launches Play Billing on the `play` flavor;
 * where there's no billing backend, debug builds simulate a purchase so the unlock flow is testable.
 */
class PaywallFragment : Fragment(R.layout.fragment_paywall) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val app = requireActivity().application as XpendiqApplication
        app.entitlement.refresh()

        val headline = view.findViewById<TextView>(R.id.paywall_headline)
        val subhead = view.findViewById<TextView>(R.id.paywall_subhead)
        val summary = view.findViewById<TextView>(R.id.paywall_summary)
        val lockedNote = view.findViewById<TextView>(R.id.paywall_locked_note)

        when (val state = app.entitlement.state.value) {
            is EntitlementState.InTrial -> {
                headline.setText(R.string.paywall_headline_trial)
                subhead.text = getString(R.string.paywall_subhead_trial, state.daysLeft)
            }
            else -> {
                headline.setText(R.string.paywall_headline_expired)
                subhead.setText(R.string.paywall_subhead_expired)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val count = app.repository.visibleCount()
            val spend = CurrencyFormat.paiseToInr(app.repository.visibleSpendInrPaise())
            summary.text = resources.getQuantityString(R.plurals.paywall_summary, count, spend, count)

            val lockedCount = app.repository.lockedCount()
            if (lockedCount > 0) {
                val lockedSpend = CurrencyFormat.paiseToInr(app.repository.lockedSpendInrPaise())
                lockedNote.text = resources.getQuantityString(
                    R.plurals.paywall_locked_note, lockedCount, lockedSpend, lockedCount,
                )
                lockedNote.visibility = View.VISIBLE
            } else {
                lockedNote.visibility = View.GONE
            }
        }

        view.findViewById<MaterialButton>(R.id.subscribe_btn).setOnClickListener { onSubscribe(app) }
        view.findViewById<MaterialButton>(R.id.not_now_btn).setOnClickListener {
            findNavController().navigateUp()
        }

        // Dismiss the paywall once the purchase lands. Real Play Billing flips the flag and refreshes
        // entitlement asynchronously (from the PurchasesUpdatedListener), so the click handler can't
        // navigate — we react to the state instead. We only reach the paywall while InTrial or
        // Expired, so a transition to Subscribed always means a purchase just completed.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.entitlement.state.collect { state ->
                    if (state is EntitlementState.Subscribed) {
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    private fun onSubscribe(app: XpendiqApplication) {
        if (app.billing.isAvailable) {
            // Real Play Billing. On success the PurchasesUpdatedListener flips the flag, refreshes
            // entitlement, and the app-scope collector unlocks the hidden rows; the Home banner /
            // gating update when we return. Nothing to do here but launch the flow.
            app.billing.launchPurchase(requireActivity())
            return
        }
        // No billing backend (free flavor, or a debug build with no Play connection): simulate a
        // purchase so the unlock-all + read-only-lift flow stays testable.
        if (BuildConfig.DEBUG) {
            // refresh() emits Subscribed, and the entitlement collector navigates up for us.
            Preferences.setSubscribed(requireContext(), true)
            app.entitlement.refresh()
        } else {
            view?.let { Snackbar.make(it, R.string.paywall_billing_soon, Snackbar.LENGTH_LONG).show() }
        }
    }
}
