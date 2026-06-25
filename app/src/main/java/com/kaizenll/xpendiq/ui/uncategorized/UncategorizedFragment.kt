package com.kaizenll.xpendiq.ui.uncategorized

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
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
import com.kaizenll.xpendiq.entitlement.entitlement
import com.kaizenll.xpendiq.entitlement.requireEntitledToEdit
import com.kaizenll.xpendiq.util.Motion
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class UncategorizedFragment : Fragment(R.layout.fragment_uncategorized) {

    private val viewModel: UncategorizedViewModel by activityViewModels()

    private lateinit var toolbar: MaterialToolbar
    private lateinit var adapter: UncategorizedAdapter
    private lateinit var backCallback: OnBackPressedCallback
    private lateinit var suggestionsBlock: View
    private lateinit var suggestionsContainer: LinearLayout
    private var latestSuggestions: List<UncategorizedViewModel.Suggestion> = emptyList()
    // True while a suggestion row is playing its swipe-away animation, so an incoming flow
    // emission doesn't rebuild the block and snap the row out mid-animation.
    private var applyingSuggestion = false
    private var reviewCount = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar = view.findViewById(R.id.toolbar)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        val empty = view.findViewById<View>(R.id.empty)
        val tabs = view.findViewById<TabLayout>(R.id.tabs)
        suggestionsBlock = view.findViewById(R.id.suggestions_block)
        suggestionsContainer = view.findViewById(R.id.suggestions_container)

        adapter = UncategorizedAdapter(
            onClick = { txn ->
                // Categorizing is an edit — gated when the trial has ended.
                if (requireEntitledToEdit(requireView())) {
                    CategoryPickerSheet.newInstance(txn.id).show(parentFragmentManager, "category_picker")
                }
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.suggestions.collect { suggestions ->
                    latestSuggestions = suggestions
                    if (!applyingSuggestion) renderSuggestions()
                }
            }
        }
    }

    /**
     * Rebuilds the suggestions block. Hidden while bulk-selecting (the toolbar is busy) or when
     * there is nothing to suggest. Rows are inflated fresh each time — there are at most three.
     */
    private fun renderSuggestions() {
        // Suggestions apply a category (an edit), so they're hidden in the read-only state too.
        val show = latestSuggestions.isNotEmpty() && !adapter.selectionMode && entitlement.isEntitled()
        suggestionsBlock.visibility = if (show) View.VISIBLE else View.GONE
        suggestionsContainer.removeAllViews()
        if (!show) return

        val inflater = LayoutInflater.from(requireContext())
        for (s in latestSuggestions) {
            val row = inflater.inflate(R.layout.item_suggestion, suggestionsContainer, false)
            bindSuggestion(row, s)
            suggestionsContainer.addView(row)
        }
    }

    private fun bindSuggestion(row: View, s: UncategorizedViewModel.Suggestion) {
        val avatar = row.findViewById<TextView>(R.id.avatar)
        val color = runCatching { Color.parseColor(s.category.colorHex) }.getOrNull()
        avatar.text = s.display.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
        avatar.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor((color ?: Color.GRAY).let { (it and 0x00FFFFFF) or 0x38000000 })
        }
        if (color != null) avatar.setTextColor(color)

        row.findViewById<TextView>(R.id.suggestion_merchant).text = s.display
        row.findViewById<TextView>(R.id.suggestion_detail).text =
            resources.getQuantityString(R.plurals.uncat_suggestion_detail, s.count, s.count, s.category.name)
        row.findViewById<MaterialButton>(R.id.apply_btn).setOnClickListener {
            applySuggestionAnimated(row, s)
        }
    }

    /**
     * Apply a suggestion with the "suggestion clears" motion (design system, demo 8): the row
     * swipes off to the right while fading, then its height collapses so the rows below slide up.
     * Only after the row is gone do we commit the recategorize, so the DB-driven rebuild lands on
     * an already-cleared block instead of snapping the row out mid-animation.
     */
    private fun applySuggestionAnimated(row: View, s: UncategorizedViewModel.Suggestion) {
        if (applyingSuggestion) return
        row.findViewById<MaterialButton>(R.id.apply_btn).isEnabled = false

        if (!Motion.enabled(requireContext())) {
            viewModel.applySuggestion(s)
            return
        }

        applyingSuggestion = true
        row.animate()
            .translationX(row.width * 1.1f)
            .alpha(0f)
            .setDuration(260L)
            .setInterpolator(Motion.accelerate)
            .withEndAction { collapseRowThenApply(row, s) }
            .start()
    }

    private fun collapseRowThenApply(row: View, s: UncategorizedViewModel.Suggestion) {
        ValueAnimator.ofInt(row.height, 0).apply {
            duration = 240L
            interpolator = Motion.emphasized
            addUpdateListener {
                row.layoutParams = row.layoutParams.also { it.height = animatedValue as Int }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    applyingSuggestion = false
                    // Commit now; the DB write re-emits suggestions and rebuilds the block.
                    viewModel.applySuggestion(s)
                }
            })
            start()
        }
    }

    private fun updateToolbar() {
        // Selection mode owns the toolbar; keep the suggestions block out of the way.
        if (::suggestionsBlock.isInitialized && !applyingSuggestion) renderSuggestions()
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
            if (ids.isNotEmpty() && requireEntitledToEdit(requireView())) {
                CategoryPickerSheet.newInstanceBulk(ids).show(parentFragmentManager, "category_picker")
            }
            true
        }
        R.id.action_bulk_delete -> {
            if (requireEntitledToEdit(requireView())) confirmBulkDelete(adapter.selectedIds())
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
