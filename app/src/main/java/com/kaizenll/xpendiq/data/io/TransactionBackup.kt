package com.kaizenll.xpendiq.data.io

import com.kaizenll.xpendiq.data.dao.CategoryDao
import com.kaizenll.xpendiq.data.db.CategoryPalette
import com.kaizenll.xpendiq.data.db.DatabaseSeeder
import com.kaizenll.xpendiq.data.db.XpendiqDatabase
import com.kaizenll.xpendiq.data.entity.Category
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
 * CSV export / import of the whole transaction history — the "switch phones" backup. The format
 * is human-readable (opens cleanly in a spreadsheet) yet carries enough to rebuild each row
 * faithfully, including its category (by name + type) and its `smsBodyHash` so a re-import never
 * duplicates and a future SMS scan still dedupes against restored rows.
 */
object TransactionBackup {

    private val HEADER = listOf(
        "date", "type", "amount", "currency", "category", "merchant",
        "paymentMode", "notes", "accountTail", "merchantNormalized",
        "sender", "isUserEdited", "smsBodyHash", "smsBody",
    )

    // UTF-8 byte-order mark, written as raw bytes so Excel reads non-ASCII merchant names as UTF-8.
    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private const val BOM_CODE = 0xFEFF

    private val zone: ZoneId = ZoneId.systemDefault()
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    /** Outcome of an import: how many rows were added, deduped away, or unparseable. */
    data class ImportResult(val imported: Int, val skipped: Int, val failed: Int)

    /** Writes every transaction (newest first) to [out] as CSV. Returns the row count written. */
    suspend fun export(db: XpendiqDatabase, out: OutputStream): Int = withContext(Dispatchers.IO) {
        val txns = db.transactionDao().findAll().sortedByDescending { it.occurredAt }
        val catsById = db.categoryDao().getAll().associateBy { it.id }
        out.write(UTF8_BOM)
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.appendLine(Csv.encodeRow(HEADER))
            for (t in txns) {
                w.appendLine(Csv.encodeRow(rowFor(t, catsById[t.categoryId])))
            }
        }
        txns.size
    }

    private fun rowFor(t: TransactionEntity, category: Category?): List<String> = listOf(
        LocalDateTime.ofInstant(Instant.ofEpochMilli(t.occurredAt), zone).format(dateFmt),
        t.type.name,
        String.format(Locale.ROOT, "%.2f", t.amountPaise / 100.0),
        t.currency,
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
     * Reads CSV from [input] and merges it in. Rows whose `smsBodyHash` already exists are skipped
     * (the UNIQUE index + insert-IGNORE), so importing onto a partially-populated phone — or
     * importing the same file twice — never duplicates. Categories are resolved by name + type and
     * recreated if missing, so custom categories survive the move.
     */
    suspend fun import(db: XpendiqDatabase, input: InputStream): ImportResult =
        withContext(Dispatchers.IO) {
            var text = input.readBytes().toString(Charsets.UTF_8)
            if (text.isNotEmpty() && text[0].code == BOM_CODE) text = text.substring(1)
            val rows = Csv.parse(text)
            if (rows.isEmpty()) return@withContext ImportResult(0, 0, 0)

            val header = rows.first().map { it.trim() }
            val colIndex = header.withIndex().associate { (i, h) -> h to i }
            val txnDao = db.transactionDao()
            val categoryDao = db.categoryDao()
            val categoryCache = HashMap<Pair<String, TransactionType>, Long>()
            val now = System.currentTimeMillis()

            var imported = 0
            var skipped = 0
            var failed = 0

            for (r in rows.drop(1)) {
                if (r.size == 1 && r[0].isBlank()) continue // trailing blank line
                fun col(name: String): String = colIndex[name]?.let { r.getOrNull(it) }.orEmpty()
                try {
                    val type = TransactionType.valueOf(col("type").trim())
                    val occurredAt = LocalDateTime.parse(col("date").trim(), dateFmt)
                        .atZone(zone).toInstant().toEpochMilli()
                    val amountPaise = Math.round(col("amount").trim().toDouble() * 100.0)
                    val currency = col("currency").trim().ifBlank { "INR" }
                    val paymentMode = runCatching { PaymentMode.valueOf(col("paymentMode").trim()) }
                        .getOrDefault(PaymentMode.UNKNOWN)
                    val categoryName = col("category").trim().ifBlank { "Uncategorized" }
                    val categoryId = resolveCategoryId(categoryDao, categoryCache, categoryName, type)
                    val hash = col("smsBodyHash").ifBlank { "import:${UUID.randomUUID()}" }

                    val entity = TransactionEntity(
                        amountPaise = amountPaise,
                        currency = currency,
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
                        isUserEdited = col("isUserEdited").trim().equals("true", ignoreCase = true),
                        createdAt = now,
                        updatedAt = now,
                    )
                    if (txnDao.insert(entity) == -1L) skipped++ else imported++
                } catch (e: Exception) {
                    failed++
                }
            }
            ImportResult(imported, skipped, failed)
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
}
