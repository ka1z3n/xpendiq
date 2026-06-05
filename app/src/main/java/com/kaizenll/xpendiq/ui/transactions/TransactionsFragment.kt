package com.kaizenll.xpendiq.ui.transactions

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.util.Motion
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class TransactionsFragment : Fragment(R.layout.fragment_transactions) {

    private val viewModel: TransactionsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        val empty = view.findViewById<View>(R.id.empty)
        val tabs = view.findViewById<TabLayout>(R.id.tabs)

        val adapter = TransactionsAdapter(onRowClick = { row ->
            TransactionDetailSheet.newInstance(row.txn.id)
                .show(parentFragmentManager, "txn_detail")
        })
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setSelectedType(
                    if (tab.position == 0) TransactionType.DEBIT else TransactionType.CREDIT
                )
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        // Reflect current ViewModel state on first attach (handles config changes).
        val initialTab = if (viewModel.selectedType.value == TransactionType.CREDIT) 1 else 0
        tabs.getTabAt(initialTab)?.select()

        view.findViewById<FloatingActionButton>(R.id.add_fab).setOnClickListener {
            findNavController().navigate(R.id.editTransactionFragment)
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
