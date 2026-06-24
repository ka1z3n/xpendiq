package com.kaizenll.xpendiq.data.io

import com.kaizenll.xpendiq.data.dao.CategoryDao
import com.kaizenll.xpendiq.data.dao.MerchantRuleDao
import com.kaizenll.xpendiq.data.db.CategoryPalette
import com.kaizenll.xpendiq.data.db.DatabaseSeeder
import com.kaizenll.xpendiq.data.db.XpendiqDatabase
import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.MerchantRule
import com.kaizenll.xpendiq.data.entity.MerchantRuleSource
import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.util.Csv
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CSV export / import of the whole app state — the "switch phones" backup. One file holds three
 * labelled sections (categories, learned merchant rules, transactions) so a restore brings back
 * not just the history but the user's custom categories and the rules that auto-file future SMS.
 *
 * The format is human-readable (opens in a spreadsheet, UTF-8 BOM, RFC-4180 quoting). Import is a
 * non-destructive merge: transactions dedupe on `smsBodyHash`, categories upsert by name + type,
 * and merchant rules are skipped when an equivalent pattern already exists. A legacy
 * transactions-only CSV (no section markers) still imports correctly.
 */
object TransactionBackup {

    private const val SECTION_PREFIX = "#SECTION:"
    private const val SECTION_CATEGORIES = "CATEGORIES"
    private const val SECTION_RULES = "MERCHANT_RULES"
    private const val SECTION_TRANSACTIONS = "TRANSACTIONS"

    private val CATEGORY_HEADER = listOf(
        "name", "type", "iconKey", "colorHex", "isSystem", "sortOrder", "excludedFromTotals",
    )
    private val RULE_HEADER = listOf("pattern", "category", "categoryType", "priority")
    private val TXN_HEADER = listOf(
        "date", "type", "amount", "currency", "amountInr", "category", "merchant",
        "paymentMode", "notes", "accountTail", "merchantNormalized",
        "sender", "isUserEdited", "smsBodyHash", "smsBody",
    )

    // UTF-8 byte-order mark, written as raw bytes so Excel reads non-ASCII merchant names as UTF-8.
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private const val BOM_CODE = 0xFEFF

    private val zone: ZoneId = ZoneId.systemDefault()
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /** Outcome of an import. Transaction counts are the headline; the rest is restored quietly. */
    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val failed: Int,
        val categories: Int = 0,
        val rules: Int = 0,
    )

    /** Writes categories, learned rules, then every transaction to [out]. Returns txn count. */
    suspend fun export(db: XpendiqDatabase, out: OutputStream): Int = withContext(Dispatchers.IO) {
        val categories = db.categoryDao().getAll()
        val catsById = categories.associateBy { it.id }
        val txns = db.transactionDao().findAll().sortedByDescending { it.occurredAt }
        val userRules = TransactionType.values()
            .flatMap { db.merchantRuleDao().findForType(it) }
            .filter { it.source == MerchantRuleSource.USER_LEARNED }

        out.write(UTF8_BOM)
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.appendLine(SECTION_PREFIX + SECTION_CATEGORIES)
            w.appendLine(Csv.encodeRow(CATEGORY_HEADER))
            for (c in categories) w.appendLine(Csv.encodeRow(categoryRow(c)))
            w.appendLine()

            w.appendLine(SECTION_PREFIX + SECTION_RULES)
            w.appendLine(Csv.encodeRow(RULE_HEADER))
            for (r in userRules) catsById[r.categoryId]?.let { w.appendLine(Csv.encodeRow(ruleRow(r, it))) }
            w.appendLine()

            w.appendLine(SECTION_PREFIX + SECTION_TRANSACTIONS)
            w.appendLine(Csv.encodeRow(TXN_HEADER))
            for (t in txns) w.appendLine(Csv.encodeRow(txnRow(t, catsById[t.categoryId])))
        }
        txns.size
    }

    private fun categoryRow(c: Category): List<String> = listOf(
        c.name, c.appliesToType.name, c.iconKey, c.colorHex,
        c.isSystem.toString(), c.sortOrder.toString(), c.excludedFromTotals.toString(),
    )

    private fun ruleRow(r: MerchantRule, category: Category): List<String> = listOf(
        r.pattern, category.name, r.appliesToType.name, r.priority.toString(),
    )

    private fun txnRow(t: TransactionEntity, category: Category?): List<String> = listOf(
        LocalDateTime.ofInstant(Instant.ofEpochMilli(t.occurredAt), zone).format(dateFmt),
        t.type.name,
        String.format(Locale.ROOT, "%.2f", t.amountPaise / 100.0),
        t.currency,
        t.amountInrPaise?.let { String.format(Locale.ROOT, "%.2f", it / 100.0) }.orEmpty(),
        category?.name ?: "Uncategorized",
        t.merchantRaw.orEmpty(),
        t.paymentMode.name,
        t.notes.orEmpty(),
        t.accountTail.orEmpty(),
        t.merchantNormalized.orEmpty(),
        t.sender.orEmpty(),
        t.isUserEdited.toString(),
        t.smsBodyHash,
        t.smsBody.orEmpty(),
    )

    /**
     * Reads the backup from [input] and merges it. Order matters: categories first (so rules and
     * transactions resolve to them), then rules, then transactions.
     */
    suspend fun import(db: XpendiqDatabase, input: InputStream): ImportResult =
        withContext(Dispatchers.IO) {
            var text = input.readBytes().toString(Charsets.UTF_8)
            if (text.isNotEmpty() && text[0].code == BOM_CODE) text = text.substring(1)
            val sections = sectionize(Csv.parse(text))

            val categoryDao = db.categoryDao()
            val categoryCache = HashMap<Pair<String, TransactionType>, Long>()

            val catCount = importCategories(categoryDao, sections[SECTION_CATEGORIES])
            val ruleCount = importRules(
                categoryDao, db.merchantRuleDao(), categoryCache, sections[SECTION_RULES],
            )
            val txn = importTransactions(db, categoryDao, categoryCache, sections[SECTION_TRANSACTIONS])
            txn.copy(categories = catCount, rules = ruleCount)
        }

    /** Split parsed rows by `#SECTION:` markers. Unmarked content is treated as transactions. */
    private fun sectionize(rows: List<List<String>>): Map<String, List<List<String>>> {
        val out = LinkedHashMap<String, MutableList<List<String>>>()
        var current = SECTION_TRANSACTIONS
        for (row in rows) {
            val first = row.firstOrNull()?.trim().orEmpty()
            if (first.startsWith(SECTION_PREFIX)) {
                current = first.removePrefix(SECTION_PREFIX).trim()
                out.getOrPut(current) { mutableListOf() }
            } else {
                out.getOrPut(current) { mutableListOf() }.add(row)
            }
        }
        return out
    }

    private fun isBlankRow(r: List<String>): Boolean = r.size == 1 && r[0].isBlank()

    /** Build a `header name -> field value` accessor for a data row. */
    private fun accessor(header: List<String>, row: List<String>): (String) -> String {
        val idx = header.withIndex().associate { (i, h) -> h.trim() to i }
        return { name -> idx[name]?.let { row.getOrNull(it) }.orEmpty() }
    }

    /** Upsert categories by name + type so custom icons/colours and the excluded flag restore. */
    private suspend fun importCategories(
        categoryDao: CategoryDao,
        rows: List<List<String>>?,
    ): Int {
        if (rows.isNullOrEmpty()) return 0
        val header = rows.first()
        var count = 0
        for (r in rows.drop(1)) {
            if (isBlankRow(r)) continue
            val col = accessor(header, r)
            try {
                val name = col("name").trim().ifBlank { continue }
                val type = TransactionType.valueOf(col("type").trim())
                val iconKey = col("iconKey").ifBlank { Category.ICON_OTHER }
                val colorHex = col("colorHex").ifBlank { CategoryPalette.colorFor(name, type) }
                val sortOrder = col("sortOrder").trim().toIntOrNull() ?: 100
                val excluded = col("excludedFromTotals").trim().equals("true", ignoreCase = true)
                val existing = categoryDao.findByNameAndType(name, type)
                if (existing != null) {
                    // Preserve id + isSystem; refresh the user-facing styling from the backup.
                    categoryDao.update(
                        existing.copy(
                            iconKey = iconKey, colorHex = colorHex,
                            sortOrder = sortOrder, excludedFromTotals = excluded,
                        )
                    )
                } else {
                    categoryDao.insert(
                        Category(
                            name = name, iconKey = iconKey, colorHex = colorHex,
                            isSystem = col("isSystem").trim().equals("true", ignoreCase = true),
                            sortOrder = sortOrder, appliesToType = type, excludedFromTotals = excluded,
                        )
                    )
                    count++
                }
            } catch (e: Exception) {
                // Skip malformed category rows; transactions can still recreate by name later.
            }
        }
        return count
    }

    /** Restore learned merchant rules, skipping any pattern+type already present (idempotent). */
    private suspend fun importRules(
        categoryDao: CategoryDao,
        ruleDao: MerchantRuleDao,
        cache: HashMap<Pair<String, TransactionType>, Long>,
        rows: List<List<String>>?,
    ): Int {
        if (rows.isNullOrEmpty()) return 0
        val header = rows.first()
        var count = 0
        for (r in rows.drop(1)) {
            if (isBlankRow(r)) continue
            val col = accessor(header, r)
            try {
                val pattern = col("pattern").trim().ifBlank { continue }
                val type = TransactionType.valueOf(col("categoryType").trim())
                if (ruleDao.existsByPatternAndType(pattern, type)) continue
                val categoryName = col("category").trim().ifBlank { continue }
                val priority = col("priority").trim().toIntOrNull() ?: USER_RULE_PRIORITY
                val categoryId = resolveCategoryId(categoryDao, cache, categoryName, type)
                ruleDao.upsert(
                    MerchantRule(
                        pattern = pattern,
                        categoryId = categoryId,
                        appliesToType = type,
                        priority = priority,
                        source = MerchantRuleSource.USER_LEARNED,
                    )
                )
                count++
            } catch (e: Exception) {
                // Skip malformed rule rows.
            }
        }
        return count
    }

    private suspend fun importTransactions(
        db: XpendiqDatabase,
        categoryDao: CategoryDao,
        cache: HashMap<Pair<String, TransactionType>, Long>,
        rows: List<List<String>>?,
    ): ImportResult {
        if (rows.isNullOrEmpty()) return ImportResult(0, 0, 0)
        val header = rows.first()
        val txnDao = db.transactionDao()
        val now = System.currentTimeMillis()
        var imported = 0
        var skipped = 0
        var failed = 0

        for (r in rows.drop(1)) {
            if (isBlankRow(r)) continue
            val col = accessor(header, r)
            try {
                val type = TransactionType.valueOf(col("type").trim())
                val occurredAt = LocalDateTime.parse(col("date").trim(), dateFmt)
                    .atZone(zone).toInstant().toEpochMilli()
                val amountPaise = Math.round(col("amount").trim().toDouble() * 100.0)
                val currency = col("currency").trim().ifBlank { "INR" }
                // Restore the frozen INR-equivalent when present (older/legacy CSVs omit it; such
                // foreign rows fill in when the user next sets the rate).
                val amountInrPaise = col("amountInr").trim()
                    .takeIf { it.isNotBlank() }
                    ?.toDoubleOrNull()
                    ?.let { Math.round(it * 100.0) }
                val paymentMode = runCatching { PaymentMode.valueOf(col("paymentMode").trim()) }
                    .getOrDefault(PaymentMode.UNKNOWN)
                val categoryName = col("category").trim().ifBlank { "Uncategorized" }
                val categoryId = resolveCategoryId(categoryDao, cache, categoryName, type)
                val hash = col("smsBodyHash").ifBlank { "import:${UUID.randomUUID()}" }

                val entity = TransactionEntity(
                    amountPaise = amountPaise,
                    currency = currency,
                    amountInrPaise = amountInrPaise,
                    type = type,
                    paymentMode = paymentMode,
                    merchantRaw = col("merchant").ifBlank { null },
                    merchantNormalized = col("merchantNormalized").ifBlank { null },
                    accountTail = col("accountTail").ifBlank { null },
                    categoryId = categoryId,
                    occurredAt = occurredAt,
                    smsId = null,
                    smsBodyHash = hash,
                    smsBody = col("smsBody").ifBlank { null },
                    sender = col("sender").ifBlank { null },
                    notes = col("notes").ifBlank { null },
                    // Always user-edited on import: a restored row must survive the startup
                    // StaleTransactionCleanup, which deletes non-edited rows the current parser
                    // would now reject. (The CSV's isUserEdited column is kept for reference.)
                    isUserEdited = true,
                    createdAt = now,
                    updatedAt = now,
                )
                if (txnDao.insert(entity) == -1L) skipped++ else imported++
            } catch (e: Exception) {
                failed++
            }
        }
        return ImportResult(imported, skipped, failed)
    }

    /** Find the category by name + type, creating a faithful copy if this device doesn't have it. */
    private suspend fun resolveCategoryId(
        categoryDao: CategoryDao,
        cache: HashMap<Pair<String, TransactionType>, Long>,
        name: String,
        type: TransactionType,
    ): Long {
        cache[name to type]?.let { return it }
        val existing = categoryDao.findByNameAndType(name, type)
        if (existing != null) {
            cache[name to type] = existing.id
            return existing.id
        }
        val created = Category(
            name = name,
            iconKey = Category.ICON_OTHER,
            colorHex = CategoryPalette.colorFor(name, type),
            isSystem = false,
            sortOrder = 100,
            appliesToType = type,
            excludedFromTotals = name in DatabaseSeeder.EXCLUDED_FROM_TOTALS,
        )
        val id = categoryDao.insert(created)
        cache[name to type] = id
        return id
    }

    // Matches TransactionRepository.USER_RULE_PRIORITY — user rules beat seeded ones (sorted ASC).
    private const val USER_RULE_PRIORITY = 50
}
