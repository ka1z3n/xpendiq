package com.kaizenll.xpendiq.ui.categories

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.data.entity.Category
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CategoriesFragment : Fragment(R.layout.fragment_categories) {

    private val viewModel: CategoriesViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recycler)
        val adapter = CategoriesAdapter(
            onRowClick = { row -> openEdit(row.category) },
            onRowLongClick = { row -> showRowMenu(row.category) },
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        view.findViewById<ExtendedFloatingActionButton>(R.id.fab).setOnClickListener {
            CategoryEditDialog.forAdd().show(parentFragmentManager, "cat_edit")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { adapter.submitList(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is CategoryEvent.NameBlank ->
                            Snackbar.make(view, R.string.cats_name_required, Snackbar.LENGTH_SHORT).show()
                        is CategoryEvent.Saved -> Unit
                        is CategoryEvent.Deleted ->
                            Snackbar.make(view, R.string.cats_deleted, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun openEdit(category: Category) {
        if (category.isSystem) {
            Snackbar.make(requireView(), R.string.cats_system_cannot_edit, Snackbar.LENGTH_SHORT).show()
            return
        }
        CategoryEditDialog.forEdit(category).show(parentFragmentManager, "cat_edit")
    }

    private fun showRowMenu(category: Category) {
        if (category.isSystem) {
            Snackbar.make(requireView(), R.string.cats_system_cannot_edit, Snackbar.LENGTH_SHORT).show()
            return
        }
        val items = arrayOf(getString(R.string.cats_action_edit), getString(R.string.cats_action_delete))
        AlertDialog.Builder(requireContext())
            .setTitle(category.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> openEdit(category)
                    1 -> confirmDelete(category)
                }
            }
            .show()
    }

    private fun confirmDelete(category: Category) {
        viewLifecycleOwner.lifecycleScope.launch {
            val usage = viewModel.usageCount(category)
            val options = viewModel.reassignmentOptions(category)
            if (options.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.cats_cannot_delete_title)
                    .setMessage(R.string.cats_cannot_delete_msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@launch
            }
            if (usage == 0) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.cats_delete_title, category.name))
                    .setMessage(R.string.cats_delete_empty_msg)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.cats_action_delete) { _, _ ->
                        viewModel.deleteCategory(category, options.first().id)
                    }
                    .show()
            } else {
                val labels = options.map { it.name }.toTypedArray()
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.cats_delete_title, category.name))
                    .setMessage(getString(R.string.cats_delete_reassign_msg, usage))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setItems(labels) { _, which ->
                        viewModel.deleteCategory(category, options[which].id)
                    }
                    .show()
            }
        }
    }
}
