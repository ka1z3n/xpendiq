package com.kaizenll.xpendiq.ui.categories

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
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.TransactionType
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

    /** Colours already in use, per type — filtered out of the picker so categories stay distinct. */
    private var usedByType: Map<TransactionType, Set<String>> = emptyMap()

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
        typeInput.setOnItemClickListener { _, _, pos, _ ->
            selectedType = types[pos]
            renderSwatches(swatchRow, selectedType)
        }

        title.setText(R.string.cats_add_title)

        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as XpendiqApplication
            val all = withContext(Dispatchers.IO) { app.database.categoryDao().getAll() }
            // Taken colours per type. The category being edited is excluded so its own colour
            // stays selectable.
            usedByType = all
                .filterNot { it.id == editId }
                .groupBy({ it.appliesToType }, { it.colorHex.uppercase() })
                .mapValues { entry -> entry.value.toSet() }

            val cat = editId?.let { id -> all.firstOrNull { it.id == id } }
            if (editId != null && cat == null) { dismiss(); return@launch }
            if (cat != null) {
                existing = cat
                title.setText(R.string.cats_edit_title)
                nameInput.setText(cat.name)
                typeInput.setText(typeLabel(cat.appliesToType), false)
                typeLayout.isEnabled = false  // type is immutable once created
                selectedType = cat.appliesToType
                selectedColor = cat.colorHex
            }
            renderSwatches(swatchRow, selectedType)
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

    /** Palette minus colours already used by other categories of [type]; never empty. */
    private fun availableColors(type: TransactionType): List<String> {
        val used = usedByType[type].orEmpty()
        return PALETTE.filter { it.uppercase() !in used }.ifEmpty { PALETTE }
    }

    private fun renderSwatches(row: LinearLayout, type: TransactionType) {
        val colors = availableColors(type)
        if (colors.none { it.equals(selectedColor, ignoreCase = true) }) {
            selectedColor = colors.first()
        }
        row.removeAllViews()
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 36f, resources.displayMetrics,
        ).toInt()
        colors.forEach { hex ->
            val swatch = FrameLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(px, px).apply { setMargins(8, 8, 8, 8) }
                background = makeSwatch(hex, selected = hex.equals(selectedColor, ignoreCase = true))
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
            child.background = makeSwatch(hex, selected = hex.equals(selectedColor, ignoreCase = true))
        }
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
            // Material 500s
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
            "#CDDC39",  // lime
            "#FFEB3B",  // yellow
            "#FFC107",  // amber
            "#FF9800",  // orange
            "#FF5722",  // deep orange
            "#795548",  // brown
            "#607D8B",  // blue grey
            "#9E9E9E",  // grey
            // Darker 700s
            "#D32F2F",  // red 700
            "#C2185B",  // pink 700
            "#7B1FA2",  // purple 700
            "#303F9F",  // indigo 700
            "#1976D2",  // blue 700
            "#00796B",  // teal 700
            "#388E3C",  // green 700
            "#F57C00",  // orange 700
            "#E64A19",  // deep orange 700
            "#5D4037",  // brown 700
            "#455A64",  // blue grey 700
            // Lighter 300s + accents
            "#EF9A9A",  // red 300
            "#F48FB1",  // pink 300
            "#CE93D8",  // purple 300
            "#9FA8DA",  // indigo 300
            "#90CAF9",  // blue 300
            "#80CBC4",  // teal 300
            "#A5D6A7",  // green 300
            "#FFE082",  // amber 300
            "#FFCC80",  // orange 300
            "#7C4DFF",  // deep purple accent
            "#FF4081",  // pink accent
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
