package com.kaizenll.xpendiq.ui.paywall

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
 * banner and the "subscribe to edit" prompts. The Subscribe button is a stub until Google Play
 * Billing lands (step F): debug builds simulate a purchase so the unlock flow is testable.
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
    }

    private fun onSubscribe(app: XpendiqApplication) {
        if (BuildConfig.DEBUG) {
            // Simulate a purchase so the unlock-all + read-only-lift flow can be verified.
            Preferences.setSubscribed(requireContext(), true)
            app.entitlement.refresh()
            view?.let { Snackbar.make(it, R.string.paywall_subscribed_toast, Snackbar.LENGTH_SHORT).show() }
            findNavController().navigateUp()
        } else {
            view?.let { Snackbar.make(it, R.string.paywall_billing_soon, Snackbar.LENGTH_LONG).show() }
        }
    }
}
