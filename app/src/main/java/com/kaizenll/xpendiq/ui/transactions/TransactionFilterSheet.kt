package com.kaizenll.xpendiq.ui.transactions

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Two-dimension filter for the Transactions list — category (color-dot chips) and payment mode —
 * styled after the "Transactions Filter" design. Shown from the parent fragment's child manager so
 * it shares that fragment's [TransactionsViewModel] instance and the list updates on Apply.
 */
class TransactionFilterSheet : BottomSheetDialogFragment() {

    private val viewModel: TransactionsViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private val selectedCategories = mutableSetOf<Long>()
    private val selectedModes = mutableSetOf<PaymentMode>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.sheet_transaction_filter, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectedCategories.apply { clear(); addAll(viewModel.categoryFilter.value) }
        selectedModes.apply { clear(); addAll(viewModel.paymentFilter.value) }

        val categorySection = view.findViewById<View>(R.id.category_section)
        val paymentSection = view.findViewById<View>(R.id.payment_section)
        val categoryGroup = view.findViewById<ChipGroup>(R.id.category_group)
        val paymentGroup = view.findViewById<ChipGroup>(R.id.payment_group)

        buildCategoryChips(categoryGroup, categorySection)
        buildPaymentChips(paymentGroup, paymentSection)

        view.findViewById<View>(R.id.reset_btn).setOnClickListener {
            selectedCategories.clear()
            selectedModes.clear()
            uncheckAll(categoryGroup)
            uncheckAll(paymentGroup)
        }
        view.findViewById<MaterialButton>(R.id.apply_btn).setOnClickListener {
            viewModel.setFilters(selectedCategories.toSet(), selectedModes.toSet())
            dismiss()
        }
    }

    private fun buildCategoryChips(group: ChipGroup, section: View) {
        val categories = viewModel.availableCategories.value
        section.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
        for (cat in categories) {
            val chip = layoutInflater.inflate(R.layout.item_category_choice_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.text = cat.name
            val color = runCatching { Color.parseColor(cat.colorHex) }.getOrElse { Color.GRAY }
            // Colour the leading dot, and outline the chip in its own colour only when selected.
            chip.isChipIconVisible = true
            chip.chipIconTint = ColorStateList.valueOf(color)
            chip.chipStrokeColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(color, Color.TRANSPARENT),
            )
            chip.isChecked = cat.id in selectedCategories
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedCategories.add(cat.id) else selectedCategories.remove(cat.id)
            }
            group.addView(chip)
        }
    }

    private fun buildPaymentChips(group: ChipGroup, section: View) {
        val modes = viewModel.availablePaymentModes.value
        section.visibility = if (modes.isEmpty()) View.GONE else View.VISIBLE
        for (mode in modes) {
            val chip = layoutInflater.inflate(R.layout.item_filter_chip, group, false) as Chip
            chip.id = View.generateViewId()
            chip.setText(labelFor(mode))
            chip.isChecked = mode in selectedModes
            chip.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedModes.add(mode) else selectedModes.remove(mode)
            }
            group.addView(chip)
        }
    }

    private fun uncheckAll(group: ChipGroup) {
        for (i in 0 until group.childCount) (group.getChildAt(i) as? Chip)?.isChecked = false
    }

    private fun labelFor(mode: PaymentMode): Int = when (mode) {
        PaymentMode.UPI -> R.string.pm_upi
        PaymentMode.CARD_CREDIT -> R.string.pm_credit
        PaymentMode.CARD_DEBIT -> R.string.pm_debit
        PaymentMode.NETBANKING -> R.string.pm_bank
        PaymentMode.AUTO_DEBIT -> R.string.pm_auto
        PaymentMode.CASH -> R.string.pm_cash
        PaymentMode.UNKNOWN -> R.string.pm_upi // never surfaced — UNKNOWN is excluded upstream
    }
}
