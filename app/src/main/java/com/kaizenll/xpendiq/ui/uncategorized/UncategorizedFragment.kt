package com.kaizenll.xpendiq.ui.uncategorized

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.util.Motion
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class UncategorizedFragment : Fragment(R.layout.fragment_uncategorized) {

    private val viewModel: UncategorizedViewModel by activityViewModels()

    private lateinit var toolbar: MaterialToolbar
    private lateinit var adapter: UncategorizedAdapter
    private lateinit var backCallback: OnBackPressedCallback
    private var reviewCount = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        val empty = view.findViewById<View>(R.id.empty)
        val tabs = view.findViewById<TabLayout>(R.id.tabs)

        adapter = UncategorizedAdapter(
            onClick = { txn ->
                CategoryPickerSheet.newInstance(txn.id).show(parentFragmentManager, "category_picker")
            },
            onSelectionChanged = ::updateToolbar,
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // Exit selection mode on back press before leaving the screen.
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() { adapter.clearSelection() }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        // Clearing the queue via a bulk categorize → drop out of selection mode.
        parentFragmentManager.setFragmentResultListener(
            CategoryPickerSheet.RESULT_BULK_DONE, viewLifecycleOwner,
        ) { _, _ -> adapter.clearSelection() }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                adapter.clearSelection()
                viewModel.setSelectedType(
                    if (tab.position == 0) TransactionType.DEBIT else TransactionType.CREDIT
                )
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        val initialTab = if (viewModel.selectedType.value == TransactionType.CREDIT) 1 else 0
        tabs.getTabAt(initialTab)?.select()

        val staggerController =
            if (Motion.enabled(requireContext())) {
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_fall_down)
            } else {
                null
            }
        var staggered = false

        updateToolbar()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { txns ->
                    adapter.submitList(txns) {
                        if (staggerController != null && !staggered && txns.isNotEmpty()) {
                            staggered = true
                            recycler.layoutAnimation = staggerController
                            recycler.scheduleLayoutAnimation()
                        }
                    }
                    reviewCount = txns.size
                    updateToolbar()
                    empty.visibility = if (txns.isEmpty()) View.VISIBLE else View.GONE
                    recycler.visibility = if (txns.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun updateToolbar() {
        if (adapter.selectionMode) {
            toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_close)
            toolbar.setNavigationOnClickListener { adapter.clearSelection() }
            toolbar.title = getString(R.string.uncat_selected_count, adapter.selectedCount)
            if (toolbar.menu.size() == 0) toolbar.inflateMenu(R.menu.uncategorized_selection)
            toolbar.setOnMenuItemClickListener(::onSelectionMenuItem)
            backCallback.isEnabled = true
        } else {
            toolbar.menu.clear()
            toolbar.setOnMenuItemClickListener(null)
            toolbar.navigationIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_arrow_back)
            toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
            toolbar.title = getString(R.string.uncat_header_count, reviewCount)
            backCallback.isEnabled = false
        }
    }

    private fun onSelectionMenuItem(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_bulk_categorize -> {
            val ids = adapter.selectedIds()
            if (ids.isNotEmpty()) {
                CategoryPickerSheet.newInstanceBulk(ids).show(parentFragmentManager, "category_picker")
            }
            true
        }
        R.id.action_bulk_delete -> {
            confirmBulkDelete(adapter.selectedIds())
            true
        }
        else -> false
    }

    private fun confirmBulkDelete(ids: LongArray) {
        if (ids.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.uncat_bulk_delete_title, ids.size))
            .setMessage(R.string.uncat_bulk_delete_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.detail_delete) { _, _ ->
                viewModel.bulkDelete(ids.toList())
                adapter.clearSelection()
            }
            .show()
    }
}
