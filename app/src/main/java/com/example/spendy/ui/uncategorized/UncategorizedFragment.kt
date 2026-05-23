package com.example.spendy.ui.uncategorized

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.spendy.R
import com.example.spendy.data.entity.TransactionType
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class UncategorizedFragment : Fragment(R.layout.fragment_uncategorized) {

    private val viewModel: UncategorizedViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        val empty = view.findViewById<View>(R.id.empty)
        val tabs = view.findViewById<TabLayout>(R.id.tabs)

        val adapter = UncategorizedAdapter(onClick = { txn ->
            CategoryPickerSheet.newInstance(txn.id)
                .show(parentFragmentManager, "category_picker")
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

        val initialTab = if (viewModel.selectedType.value == TransactionType.CREDIT) 1 else 0
        tabs.getTabAt(initialTab)?.select()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { txns ->
                    adapter.submitList(txns)
                    empty.visibility = if (txns.isEmpty()) View.VISIBLE else View.GONE
                    recycler.visibility = if (txns.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }
}
