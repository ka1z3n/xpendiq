package com.kaizenll.xpendiq.data.db

import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.MerchantRule
import com.kaizenll.xpendiq.data.entity.MerchantRuleSource
import com.kaizenll.xpendiq.data.entity.TransactionType

data class SeedRule(
    val pattern: String,
    val categoryName: String,
    val type: TransactionType = TransactionType.DEBIT,
    val priority: Int = 100,
)

object DatabaseSeeder {

    suspend fun seed(db: XpendiqDatabase) {
        if (db.categoryDao().count() == 0) seedCategories(db)
        if (db.merchantRuleDao().count() == 0) seedMerchantRules(db)
    }

    private suspend fun seedCategories(db: XpendiqDatabase) {
        val dao = db.categoryDao()

        val spendCategories = listOf(
            "Food & Dining" to "restaurant",
            "Groceries" to "shopping_basket",
            "Transport" to "directions_car",
            "Shopping" to "shopping_bag",
            "Subscriptions" to "subscriptions",
            "Bills & Utilities" to "receipt_long",
            "Entertainment" to "movie",
            "Health" to "local_hospital",
            "Travel" to "flight",
            "Rent" to "home",
            "Transfers" to "swap_horiz",
            "Other" to Category.ICON_OTHER,
        )
        spendCategories.forEachIndexed { i, (name, icon) ->
            dao.insert(
                Category(
                    name = name,
                    iconKey = icon,
                    colorHex = CategoryPalette.colorFor(name, TransactionType.DEBIT),
                    isSystem = false,
                    sortOrder = i,
                    appliesToType = TransactionType.DEBIT,
                )
            )
        }
        dao.insert(uncategorized(TransactionType.DEBIT))

        val creditCategories = listOf(
            "Refund" to "undo",
            "Transfers (P2P UPI in)" to "call_received",
            "Transfers (CC payment)" to "credit_card",
            "Income" to "payments",
            "Cashback / Rewards" to "redeem",
            "Interest" to "trending_up",
            "Cheque / NEFT deposit" to "account_balance",
            "Other" to Category.ICON_OTHER,
        )
        creditCategories.forEachIndexed { i, (name, icon) ->
            dao.insert(
                Category(
                    name = name,
                    iconKey = icon,
                    colorHex = CategoryPalette.colorFor(name, TransactionType.CREDIT),
                    isSystem = false,
                    sortOrder = i,
                    appliesToType = TransactionType.CREDIT,
                )
            )
        }
        dao.insert(uncategorized(TransactionType.CREDIT))

        dao.insert(
            Category(
                name = "Investment",
                iconKey = "savings",
                colorHex = CategoryPalette.colorFor("Investment", TransactionType.INVESTMENT),
                isSystem = true,
                sortOrder = 0,
                appliesToType = TransactionType.INVESTMENT,
            )
        )
        dao.insert(uncategorized(TransactionType.INVESTMENT))
    }

    private fun uncategorized(type: TransactionType) = Category(
        name = "Uncategorized",
        iconKey = Category.ICON_UNCATEGORIZED,
        colorHex = CategoryPalette.UNCATEGORIZED,
        isSystem = true,
        sortOrder = 999,
        appliesToType = type,
    )

    private suspend fun seedMerchantRules(db: XpendiqDatabase) {
        val categoryDao = db.categoryDao()
        val ruleDao = db.merchantRuleDao()

        val seeds = buildSeedRules()

        val resolved = seeds.mapNotNull { seed ->
            val catId = categoryDao.findByNameAndType(seed.categoryName, seed.type)?.id ?: return@mapNotNull null
            MerchantRule(
                pattern = seed.pattern,
                categoryId = catId,
                appliesToType = seed.type,
                priority = seed.priority,
                source = MerchantRuleSource.SEED,
            )
        }
        ruleDao.insertAll(resolved)
    }

    /**
     * Public so the SeedMigration can reuse the same set when topping up existing installs.
     */
    fun buildSeedRules(): List<SeedRule> = buildList {
        // ── Food & Dining (priority 100, default) ─────────────────────────────
        listOf(
            "SWIGGY", "ZOMATO", "VANLAVINOCAFE", "BELLAMCHAI", "MYTIFOODWORKS",
            "HARLEYSFINEBAKING", "NEERATICULINARYCANVAS", "UDIPISUPAHAR",
            "BHARATIYAMFOOD", "KIREETIAAHAR", "STARBUCKS", "HOTELRAGHAVENDRA",
            "TRAVELFOODSERVICES", "CAMPAHYD",
        ).forEach { add(SeedRule(it, "Food & Dining")) }

        // ── Groceries (priority 50 for the SWIGGY-overlap ones so they beat Food) ──
        listOf(
            "INSTAMART",            // Swiggy Instamart — must beat SWIGGY rule
            "SWIGGYINSTAMAR",       // truncated form on space-stripped card SMS
            "BUNDL",                // Bundl Technologies (Swiggy parent)
            "DELIGHTFUL",           // Delightful Gourmet
            "DELIGHTFULGOUR",
            "RAMAREDDY",            // RamaReddy chicken
        ).forEach { add(SeedRule(it, "Groceries", priority = 50)) }
        listOf(
            "BLINKIT", "ZEPTO", "BIGBASKET", "RATNADEEP", "STARBAZAAR", "DUNZO",
            "LICIOUS", "FRESHTOHOME", "COUNTRYDELIGHT", "MILKBASKET",
        ).forEach { add(SeedRule(it, "Groceries")) }

        listOf(
            "UBER", "OLA", "RAPIDO", "IRCTC", "IRCTCETICKETING", "IRCTCRAILWEB",
            "HPPAYDIRECT", "PAKHIFUELSLLP", "RELAYSHYD", "T1POPNGO",
        ).forEach { add(SeedRule(it, "Transport")) }

        listOf(
            "ATRIACONVERGENCE", "JIORECHARGE", "JIO", "AIRTEL", "BBPS",
            "LIVPURE", "RAILTEL",
        ).forEach { add(SeedRule(it, "Bills & Utilities")) }

        listOf(
            "APOLLOPHARMACY", "MEDICCARE", "KCMPHARMACY", "STARHOSPITALS",
            "VASANEYECARE",
        ).forEach { add(SeedRule(it, "Health")) }

        listOf(
            "MYNTRA", "AJIO", "FLIPKART", "AMAZON", "CROMA", "LIFESTYLE", "MAXFASHION",
        ).forEach { add(SeedRule(it, "Shopping")) }

        listOf(
            "MAKEMYTRIP", "MMTRIP", "INDIGO", "INDIGOAIRLINE", "AIRINDIA",
            "CLEARTRIP", "GOIBIBO", "OYO",
        ).forEach { add(SeedRule(it, "Travel")) }

        // ── Entertainment (kept for one-off entertainment purchases) ────────────
        listOf(
            "BOOKMYSHOW", "PVR", "INOX", "NOVDIGITALENTERTAINMENT",
        ).forEach { add(SeedRule(it, "Entertainment")) }

        // ── Subscriptions (priority 50 so they beat anything in Shopping/Entertainment) ──
        listOf(
            "NETFLIX",
            "SPOTIFY",
            "PRIMEVIDEO",
            "AMAZONPRIME",
            "HOTSTAR",
            "DISNEY",
            "DISNEYHOTSTAR",
            "CURSOR",
            "CLAUDE",
            "ANTHROPIC",
            "OPENAI",
            "CHATGPT",
            "CRUNCHYROLL",
            "GOOGLEPLAY",
            "GPLAY",
            "GOOGLE",          // catches "GOOGLE *" merchant strings
            "APPLE",
            "APPLEONE",
            "APPLEMUSIC",
            "APPLETV",
            "ITUNES",
            "YTPREMIUM",
            "YOUTUBE",
            "NOTION",
            "FIGMA",
            "DROPBOX",
            "MEDIUM",
            "SUBSTACK",
            "GITHUB",
            "LINKEDIN",
        ).forEach { add(SeedRule(it, "Subscriptions", priority = 50)) }
    }
}
