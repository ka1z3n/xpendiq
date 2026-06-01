package com.kaizenll.xpendiq.categorizer

import com.kaizenll.xpendiq.data.dao.CategoryDao
import com.kaizenll.xpendiq.data.dao.MerchantRuleDao
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.parser.ParsedTransaction
import com.kaizenll.xpendiq.parser.extractor.FastagTollExtractor
import com.kaizenll.xpendiq.parser.extractor.IciciCcPaymentExtractor
import com.kaizenll.xpendiq.parser.extractor.IciciCcReversalExtractor
import com.kaizenll.xpendiq.parser.extractor.SbiNeftCreditExtractor

class Categorizer(
    private val categoryDao: CategoryDao,
    private val merchantRuleDao: MerchantRuleDao,
) {

    /**
     * Returns the category id that this parsed transaction should be filed under.
     * Falls back to the type-appropriate Uncategorized id when no rule matches.
     */
    suspend fun categorize(parsed: ParsedTransaction): Long {
        // Hard-coded routes for synthetic markers from extractors.
        when (parsed.merchantNormalized) {
            IciciCcPaymentExtractor.MERCHANT_MARKER ->
                categoryDao.findByNameAndType("Transfers (CC payment)", TransactionType.CREDIT)?.id?.let { return it }
            IciciCcReversalExtractor.MERCHANT_MARKER ->
                categoryDao.findByNameAndType("Refund", TransactionType.CREDIT)?.id?.let { return it }
            FastagTollExtractor.MERCHANT_MARKER ->
                categoryDao.findByNameAndType("Transport", TransactionType.DEBIT)?.id?.let { return it }
            SbiNeftCreditExtractor.INCOME_MARKER ->
                categoryDao.findByNameAndType("Income", TransactionType.CREDIT)?.id?.let { return it }
        }

        // INVESTMENT type only has one real category.
        if (parsed.type == TransactionType.INVESTMENT) {
            categoryDao.findByNameAndType("Investment", TransactionType.INVESTMENT)?.id?.let { return it }
        }

        // Rule lookup by merchantNormalized substring match.
        val merchant = parsed.merchantNormalized
        if (merchant != null) {
            val rules = merchantRuleDao.findForType(parsed.type)
            val hit = rules.firstOrNull { rule -> merchant.contains(rule.pattern) }
            if (hit != null) return hit.categoryId
        }

        return uncategorizedFor(parsed.type)
    }

    suspend fun uncategorizedFor(type: TransactionType): Long {
        return categoryDao.findUncategorized(type)?.id
            ?: error("Uncategorized category missing for type $type — seeding did not run.")
    }
}
