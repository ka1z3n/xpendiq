package com.kaizenll.xpendiq.ui.investments

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.ui.transactions.TransactionDetailSheet
import com.kaizenll.xpendiq.ui.transactions.TransactionsAdapter
import com.kaizenll.xpendiq.util.CurrencyFormat
import com.kaizenll.xpendiq.util.Motion
import kotlinx.coroutines.launch

class InvestmentsFragment : Fragment(R.layout.fragment_investments) {

    private val viewModel: InvestmentsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        val empty = view.findViewById<View>(R.id.empty)
        val total = view.findViewById<TextView>(R.id.month_total)

        val adapter = TransactionsAdapter(onRowClick = { row ->
            TransactionDetailSheet.newInstance(row.txn.id)
                .show(parentFragmentManager, "txn_detail")
        })
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.monthlyTotal.collect { paise ->
                    total.text = CurrencyFormat.paiseToInrOrDash(paise)
                }
            }
        }
        val staggerController =
            if (Motion.enabled(requireContext())) {
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_fall_down)
            } else {
                null
            }
        var staggered = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { items ->
                    adapter.submitList(items) {
                        if (staggerController != null && !staggered && items.isNotEmpty()) {
                            staggered = true
                            recycler.layoutAnimation = staggerController
                            recycler.scheduleLayoutAnimation()
                        }
                    }
                    empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    recycler.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }
}
