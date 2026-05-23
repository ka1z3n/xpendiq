package com.example.spendy.ui.categories

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.spendy.R
import com.example.spendy.SpendyApplication
import com.example.spendy.data.entity.Category
import com.example.spendy.data.entity.TransactionType
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoryEditDialog : BottomSheetDialogFragment() {

    private val viewModel: CategoriesViewModel by activityViewModels()

    private var selectedColor: String = PALETTE.first()
    private var existing: Category? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.sheet_category_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.title)
        val nameInput = view.findViewById<TextInputEditText>(R.id.name_input)
        val typeLayout = view.findViewById<TextInputLayout>(R.id.type_layout)
        val typeInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.type_input)
        val swatchRow = view.findViewById<LinearLayout>(R.id.swatch_row)
        val cancelBtn = view.findViewById<MaterialButton>(R.id.cancel_btn)
        val saveBtn = view.findViewById<MaterialButton>(R.id.save_btn)

        val editId = arguments?.getLong(ARG_EDIT_ID, -1L)?.takeIf { it > 0 }
        val initialTypeArg = arguments?.getString(ARG_INITIAL_TYPE)?.let { TransactionType.valueOf(it) }
            ?: TransactionType.DEBIT

        // Type dropdown — only DEBIT + CREDIT are user-addable.
        val types = listOf(TransactionType.DEBIT, TransactionType.CREDIT)
        val labels = types.map { typeLabel(it) }
        typeInput.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels))
        var selectedType =
            if (initialTypeArg == TransactionType.INVESTMENT) TransactionType.DEBIT else initialTypeArg
        typeInput.setText(typeLabel(selectedType), false)
        typeInput.setOnItemClickListener { _, _, pos, _ -> selectedType = types[pos] }

        renderSwatches(swatchRow)

        if (editId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val app = requireActivity().application as SpendyApplication
                val cat = withContext(Dispatchers.IO) { app.database.categoryDao().findById(editId) }
                if (cat == null) { dismiss(); return@launch }
                existing = cat
                title.setText(R.string.cats_edit_title)
                nameInput.setText(cat.name)
                typeInput.setText(typeLabel(cat.appliesToType), false)
                typeLayout.isEnabled = false  // type is immutable once created
                selectedType = cat.appliesToType
                setSelectedSwatch(swatchRow, cat.colorHex)
            }
        } else {
            title.setText(R.string.cats_add_title)
        }

        cancelBtn.setOnClickListener { dismiss() }
        saveBtn.setOnClickListener {
            val name = nameInput.text?.toString().orEmpty().trim()
            val current = existing
            if (current == null) {
                viewModel.addCategory(name, selectedType, selectedColor)
            } else {
                viewModel.updateCategory(current, name, selectedColor)
            }
            dismiss()
        }
    }

    private fun typeLabel(type: TransactionType): String = when (type) {
        TransactionType.DEBIT -> getString(R.string.tab_spends)
        TransactionType.CREDIT -> getString(R.string.tab_credits)
        TransactionType.INVESTMENT -> "Investments"
    }

    private fun renderSwatches(row: LinearLayout) {
        row.removeAllViews()
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 36f, resources.displayMetrics,
        ).toInt()
        PALETTE.forEach { hex ->
            val swatch = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(px, px).apply { setMargins(8, 8, 8, 8) }
                background = makeSwatch(hex, selected = hex == selectedColor)
                tag = hex
                setOnClickListener {
                    selectedColor = hex
                    refreshSwatches(row)
                }
            }
            row.addView(swatch)
        }
    }

    private fun refreshSwatches(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i) as FrameLayout
            val hex = child.tag as String
            child.background = makeSwatch(hex, selected = hex == selectedColor)
        }
    }

    private fun setSelectedSwatch(row: LinearLayout, hex: String) {
        selectedColor = if (PALETTE.contains(hex)) hex else PALETTE.first()
        refreshSwatches(row)
    }

    private fun makeSwatch(hex: String, selected: Boolean): GradientDrawable {
        val color = runCatching { Color.parseColor(hex) }.getOrElse { Color.GRAY }
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (selected) setStroke(8, Color.BLACK)
        }
    }

    companion object {
        private const val ARG_EDIT_ID = "edit_id"
        private const val ARG_INITIAL_TYPE = "initial_type"

        val PALETTE = listOf(
            "#F44336",  // red
            "#E91E63",  // pink
            "#9C27B0",  // purple
            "#673AB7",  // deep purple
            "#3F51B5",  // indigo
            "#2196F3",  // blue
            "#00BCD4",  // cyan
            "#009688",  // teal
            "#4CAF50",  // green
            "#8BC34A",  // light green
            "#FFC107",  // amber
            "#FF9800",  // orange
            "#FF5722",  // deep orange
            "#795548",  // brown
            "#607D8B",  // blue grey
            "#9E9E9E",  // grey
        )

        fun forAdd(initialType: TransactionType = TransactionType.DEBIT): CategoryEditDialog =
            CategoryEditDialog().apply {
                arguments = Bundle().apply { putString(ARG_INITIAL_TYPE, initialType.name) }
            }

        fun forEdit(category: Category): CategoryEditDialog =
            CategoryEditDialog().apply {
                arguments = Bundle().apply {
                    putLong(ARG_EDIT_ID, category.id)
                    putString(ARG_INITIAL_TYPE, category.appliesToType.name)
                }
            }
    }
}
