package com.kaizenll.xpendiq.data.db

import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.MerchantRule
import com.kaizenll.xpendiq.data.entity.MerchantRuleSource
import com.kaizenll.xpendiq.data.entity.TransactionType

/**
 * Runs every app start to bring older installs up to the current seed snapshot:
 *  - inserts any new system/user categories (e.g. Subscriptions) that exist in the seeder but
 *    are missing from the DB,
 *  - inserts any new seed [MerchantRule] entries (by pattern + type),
 *  - re-routes existing un-edited transactions whose merchant string matches a new rule.
 *
 * Idempotent. A no-op once everything is up to date.
 */
object SeedMigration {

    /** Spend categories that may need to be added to existing installs. */
    private val LATER_SPEND_CATEGORIES = listOf(
        AddedCategory(name = "Subscriptions", iconKey = "subscriptions"),
    )

    private data class AddedCategory(val name: String, val iconKey: String) {
        val colorHex: String get() = CategoryPalette.colorFor(name, TransactionType.DEBIT)
    }

    suspend fun run(db: XpendiqDatabase) {
        ensureLaterCategories(db)
        ensureSelfTransferCategory(db)
        topUpMerchantRules(db)
        applyRulesToExistingTransactions(db)
    }

    /**
     * Adds the "Self-transfer" category (excluded from totals) to both DEBIT and CREDIT for installs
     * that predate it. The MIGRATION_2_3 only back-fills the flag for the existing CC-payment bucket.
     */
    private suspend fun ensureSelfTransferCategory(db: XpendiqDatabase) {
        val dao = db.categoryDao()
        for (type in listOf(TransactionType.DEBIT, TransactionType.CREDIT)) {
            if (dao.findByNameAndType(DatabaseSeeder.SELF_TRANSFER_NAME, type) != null) continue
            dao.insert(
                Category(
                    name = DatabaseSeeder.SELF_TRANSFER_NAME,
                    iconKey = "sync_alt",
                    colorHex = CategoryPalette.colorFor(DatabaseSeeder.SELF_TRANSFER_NAME, type),
                    isSystem = false,
                    sortOrder = 100,
                    appliesToType = type,
                    excludedFromTotals = true,
                )
            )
        }
    }

    private suspend fun ensureLaterCategories(db: XpendiqDatabase) {
        val dao = db.categoryDao()
        LATER_SPEND_CATEGORIES.forEach { c ->
            if (dao.findByNameAndType(c.name, TransactionType.DEBIT) == null) {
                dao.insert(
                    Category(
                        name = c.name,
                        iconKey = c.iconKey,
                        colorHex = c.colorHex,
                        isSystem = false,
                        sortOrder = 100,
                        appliesToType = TransactionType.DEBIT,
                    )
                )
            }
        }
    }

    private suspend fun topUpMerchantRules(db: XpendiqDatabase) {
        val categoryDao = db.categoryDao()
        val ruleDao = db.merchantRuleDao()
        for (seed in DatabaseSeeder.buildSeedRules()) {
            if (ruleDao.existsByPatternAndType(seed.pattern, seed.type)) continue
            val catId = categoryDao.findByNameAndType(seed.categoryName, seed.type)?.id ?: continue
            ruleDao.upsert(
                MerchantRule(
                    pattern = seed.pattern,
                    categoryId = catId,
                    appliesToType = seed.type,
                    priority = seed.priority,
                    source = MerchantRuleSource.SEED,
                )
            )
        }
    }

    private suspend fun applyRulesToExistingTransactions(db: XpendiqDatabase) {
        val categoryDao = db.categoryDao()
        val txnDao = db.transactionDao()
        val now = System.currentTimeMillis()

        // Apply lowest-priority (largest number) first so the most specific rules
        // overwrite generic ones, mirroring the runtime Categorizer's behaviour.
        val seedsDesc = DatabaseSeeder.buildSeedRules().sortedByDescending { it.priority }
        for (seed in seedsDesc) {
            val catId = categoryDao.findByNameAndType(seed.categoryName, seed.type)?.id ?: continue
            txnDao.applySeedRecategorizeByPattern(
                pattern = seed.pattern,
                type = seed.type,
                newCategoryId = catId,
                now = now,
            )
        }
    }
}
