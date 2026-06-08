package com.kaizenll.xpendiq.ui.transactions

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.data.entity.TransactionType
import android.widget.TextView
import com.kaizenll.xpendiq.util.Motion
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class TransactionsFragment : Fragment(R.layout.fragment_transactions) {

    private val viewModel: TransactionsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        val empty = view.findViewById<View>(R.id.empty)
        val btnSpends = view.findViewById<TextView>(R.id.btn_spends)
        val btnCredits = view.findViewById<TextView>(R.id.btn_credits)
        val filterBtn = view.findViewById<MaterialButton>(R.id.filter_btn)

        val adapter = TransactionsAdapter(onRowClick = { row ->
            TransactionDetailSheet.newInstance(row.txn.id)
                .show(parentFragmentManager, "txn_detail")
        })
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        btnSpends.setOnClickListener { viewModel.setSelectedType(TransactionType.DEBIT) }
        btnCredits.setOnClickListener { viewModel.setSelectedType(TransactionType.CREDIT) }

        filterBtn.setOnClickListener { showFilterDialog() }

        view.findViewById<FloatingActionButton>(R.id.add_fab).setOnClickListener {
            findNavController().navigate(R.id.editTransactionFragment, null, EDIT_NAV_OPTIONS)
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedType.collect { type ->
                    val onCredits = type == TransactionType.CREDIT
                    setSegmentSelected(btnSpends, !onCredits)
                    setSegmentSelected(btnCredits, onCredits)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.categoryFilter.collect { ids ->
                    filterBtn.text = if (ids.isEmpty()) {
                        getString(R.string.transactions_filter)
                    } else {
                        getString(R.string.transactions_filter) + " (${ids.size})"
                    }
                }
            }
        }
    }

    private fun setSegmentSelected(tv: TextView, on: Boolean) {
        tv.setBackgroundResource(if (on) R.drawable.bg_segment_on else 0)
        val attr = if (on) {
            com.google.android.material.R.attr.colorOnPrimaryContainer
        } else {
            com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        tv.setTextColor(MaterialColors.getColor(tv, attr))
    }

    private fun showFilterDialog() {
        val cats = viewModel.availableCategories.value
        if (cats.isEmpty()) return

        val active = viewModel.categoryFilter.value
        val names = cats.map { it.name }.toTypedArray()
        val checked = BooleanArray(cats.size) { cats[it].id in active }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.filter_title)
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNeutralButton(R.string.filter_clear) { _, _ -> viewModel.clearCategoryFilter() }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.filter_apply) { _, _ ->
                val selected = cats.filterIndexed { i, _ -> checked[i] }.map { it.id }.toSet()
                viewModel.setCategoryFilter(selected)
            }
            .show()
    }
}
