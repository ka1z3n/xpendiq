package com.example.spendy.data.repo

import com.example.spendy.data.dao.CategoryDao
import com.example.spendy.data.dao.MerchantRuleDao
import com.example.spendy.data.dao.TransactionDao
import com.example.spendy.data.entity.Category
import com.example.spendy.data.entity.TransactionType

class CategoryRepository(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val merchantRuleDao: MerchantRuleDao,
) {

    suspend fun add(
        name: String,
        type: TransactionType,
        colorHex: String,
        iconKey: String,
    ): Long {
        return categoryDao.insert(
            Category(
                name = name.trim(),
                iconKey = iconKey,
                colorHex = colorHex,
                isSystem = false,
                sortOrder = nextSortOrder(),
                appliesToType = type,
            )
        )
    }

    suspend fun update(category: Category, name: String, colorHex: String) {
        categoryDao.update(category.copy(name = name.trim(), colorHex = colorHex))
    }

    suspend fun usageCount(categoryId: Long): Int = transactionDao.countByCategory(categoryId)

    /** Deletes the category, first reassigning its transactions to [reassignToId] and dropping learned rules pointing at it. */
    suspend fun delete(category: Category, reassignToId: Long) {
        if (category.isSystem) error("Cannot delete a system category (${category.name}).")
        if (category.id == reassignToId) error("Cannot reassign to the category being deleted.")
        val now = System.currentTimeMillis()
        transactionDao.reassignCategory(category.id, reassignToId, now)
        merchantRuleDao.deleteByCategoryId(category.id)
        categoryDao.delete(category)
    }

    /** Used when picking a default reassignment target ("Other" preferred, falls back to Uncategorized). */
    suspend fun reassignmentOptions(forType: TransactionType, excludeId: Long): List<Category> {
        val all = categoryDao.findByType(forType)
        return all.filter { it.id != excludeId && (it.name == "Other" || (it.isSystem && it.name == "Uncategorized")) }
    }

    private fun nextSortOrder(): Int = (System.currentTimeMillis() / 1000).toInt()
}
