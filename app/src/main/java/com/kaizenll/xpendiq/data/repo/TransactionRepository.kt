package com.kaizenll.xpendiq.data.repo

import com.kaizenll.xpendiq.categorizer.Categorizer
import com.kaizenll.xpendiq.data.dao.CategoryDao
import com.kaizenll.xpendiq.data.dao.DeletedSmsHashDao
import com.kaizenll.xpendiq.data.dao.IgnoredSenderDao
import com.kaizenll.xpendiq.data.dao.MerchantRuleDao
import com.kaizenll.xpendiq.data.dao.TransactionDao
import com.kaizenll.xpendiq.data.entity.MerchantRule
import com.kaizenll.xpendiq.data.entity.MerchantRuleSource
import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.parser.SmsParser
import com.kaizenll.xpendiq.util.Fx
import com.kaizenll.xpendiq.util.Hashing
import java.util.UUID

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val deletedSmsHashDao: DeletedSmsHashDao,
    private val ignoredSenderDao: IgnoredSenderDao,
    private val merchantRuleDao: MerchantRuleDao,
    private val parser: SmsParser,
    private val categorizer: Categorizer,
    // Current manual USD→INR rate (null when unset). A lambda so the latest value is read at
    // capture time without threading Context through every caller.
    private val fxRateProvider: () -> Double? = { null },
    // Whether the user is currently entitled (in trial or subscribed). Read at capture time to
    // decide a newly-ingested row's lock state. Defaults to entitled so tests/manual paths are open.
    private val entitledProvider: () -> Boolean = { true },
) {

    enum class IngestResult {
        SAVED,
        DUPLICATE,
        DELETED_BEFORE,
        IGNORED_SENDER,
        FILTERED,
    }

    suspend fun ingestSms(
        sender: String,
        body: String,
        receivedAtMillis: Long,
        smsId: Long? = null,
        bypassSenderCheck: Boolean = false,
    ): IngestResult {
        if (ignoredSenderDao.isIgnored(sender)) return IngestResult.IGNORED_SENDER

        val parsed = parser.parse(sender, body, receivedAtMillis, requireBankSender = !bypassSenderCheck)
            ?: return IngestResult.FILTERED
        val hash = Hashing.transactionHash(sender, body)

        if (deletedSmsHashDao.exists(hash)) return IngestResult.DELETED_BEFORE
        if (transactionDao.findByHash(hash) != null) return IngestResult.DUPLICATE

        val categoryId = categorizer.categorize(parsed)
        // A learned rule may point at a category of a different type than the parsed type
        // (e.g. user moved a DEBIT SMS into the Investment category). Honour the destination
        // category's type so future similar SMS get retyped automatically.
        val finalType = categoryDao.findById(categoryId)?.appliesToType ?: parsed.type
        val now = System.currentTimeMillis()
        val entity = TransactionEntity(
            amountPaise = parsed.amountPaise,
            currency = parsed.currency,
            amountInrPaise = Fx.toInrPaise(parsed.amountPaise, parsed.currency, fxRateProvider()),
            type = finalType,
            // Captured while the trial is over and unsubscribed → hidden until the user subscribes.
            locked = !entitledProvider(),
            paymentMode = parsed.paymentMode,
            merchantRaw = parsed.merchantRaw,
            merchantNormalized = parsed.merchantNormalized,
            accountTail = parsed.accountTail,
            categoryId = categoryId,
            occurredAt = parsed.occurredAt,
            smsId = smsId,
            smsBodyHash = hash,
            smsBody = body,
            sender = sender,
            notes = null,
            isUserEdited = false,
            createdAt = now,
            updatedAt = now,
        )
        val insertedId = transactionDao.insert(entity)
        return if (insertedId == -1L) IngestResult.DUPLICATE else IngestResult.SAVED
    }

    fun observeByType(type: TransactionType) = transactionDao.observeByType(type)

    /** Reveal every locked row. Call when the user becomes entitled. Returns rows unlocked. */
    suspend fun unlockAll(): Int = transactionDao.unlockAll()

    /** Count of hidden (locked) rows — the "N transactions" half of the paywall teaser. */
    suspend fun lockedCount(): Int = transactionDao.countLocked()

    /** INR-paise spend withheld behind the paywall — the "₹X" half of the teaser. */
    suspend fun lockedSpendInrPaise(): Long = transactionDao.lockedSpendInrPaise()

    suspend fun delete(txn: TransactionEntity) {
        transactionDao.deleteById(txn.id)
        deletedSmsHashDao.insert(
            com.kaizenll.xpendiq.data.entity.DeletedSmsHash(
                smsBodyHash = txn.smsBodyHash,
                deletedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun update(txn: TransactionEntity) {
        transactionDao.update(txn.copy(isUserEdited = true, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Single-row recategorize. Honours the destination category's `appliesToType`, so picking
     * the Investment category from a DEBIT row flips both `categoryId` and `type`.
     */
    suspend fun recategorize(txn: TransactionEntity, newCategoryId: Long) {
        val destType = categoryDao.findById(newCategoryId)?.appliesToType ?: txn.type
        transactionDao.update(
            txn.copy(
                categoryId = newCategoryId,
                type = destType,
                isUserEdited = true,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Bulk recategorize by transaction id (multi-select). Honours the destination category's
     * `appliesToType` so moving rows into a different-type category flips their `type` too.
     * Does not write a merchant rule — these are explicit one-off picks across mixed merchants.
     */
    suspend fun recategorizeAll(ids: List<Long>, newCategoryId: Long) {
        val destType = categoryDao.findById(newCategoryId)?.appliesToType
        val now = System.currentTimeMillis()
        for (id in ids) {
            val txn = transactionDao.findById(id) ?: continue
            transactionDao.update(
                txn.copy(
                    categoryId = newCategoryId,
                    type = destType ?: txn.type,
                    isUserEdited = true,
                    updatedAt = now,
                )
            )
        }
    }

    /** Bulk delete by id (multi-select); each goes through [delete] so its hash is tombstoned. */
    suspend fun deleteAll(ids: List<Long>) {
        for (id in ids) {
            val txn = transactionDao.findById(id) ?: continue
            delete(txn)
        }
    }

    /**
     * "Apply to all from <merchant>" path. Writes a USER_LEARNED MerchantRule that future SMS
     * of the original type will hit, and bulk-updates every existing transaction matching the
     * merchant+oldType. If the destination category is a different type, also flips `type` on
     * those rows (and future ingest does the same flip via `ingestSms`).
     */
    suspend fun applyMerchantRuleAndRecategorize(
        merchantNormalized: String,
        oldType: TransactionType,
        newCategoryId: Long,
    ): Int {
        val destType = categoryDao.findById(newCategoryId)?.appliesToType ?: oldType
        // Rule's appliesToType matches the parse-time type; future SMS will arrive parsed as
        // that type, the rule fires, the category's appliesToType then drives the final type.
        merchantRuleDao.upsert(
            MerchantRule(
                pattern = merchantNormalized,
                categoryId = newCategoryId,
                appliesToType = oldType,
                priority = USER_RULE_PRIORITY,
                source = MerchantRuleSource.USER_LEARNED,
            )
        )
        val now = System.currentTimeMillis()
        return if (destType == oldType) {
            transactionDao.recategorizeByMerchantAndType(
                merchantNormalized = merchantNormalized,
                type = oldType,
                newCategoryId = newCategoryId,
                now = now,
            )
        } else {
            transactionDao.recategorizeByMerchantWithTypeChange(
                merchantNormalized = merchantNormalized,
                oldType = oldType,
                newType = destType,
                newCategoryId = newCategoryId,
                now = now,
            )
        }
    }

    /**
     * Inserts a transaction the user created from the "Add transaction" screen — i.e. there is
     * no source SMS. Stamps `isUserEdited = true` so future re-parses can't clobber it, and
     * generates a synthetic `smsBodyHash` so the UNIQUE index is satisfied without collisions.
     */
    suspend fun addManualTransaction(
        amountPaise: Long,
        currency: String,
        type: TransactionType,
        paymentMode: PaymentMode,
        merchantRaw: String?,
        categoryId: Long,
        occurredAt: Long,
        notes: String?,
    ): Long {
        val now = System.currentTimeMillis()
        val merchantNormalized = merchantRaw
            ?.uppercase()
            ?.replace(Regex("\\s+"), "")
            ?.takeIf { it.isNotEmpty() }
        val entity = TransactionEntity(
            amountPaise = amountPaise,
            currency = currency,
            amountInrPaise = Fx.toInrPaise(amountPaise, currency, fxRateProvider()),
            type = type,
            paymentMode = paymentMode,
            merchantRaw = merchantRaw?.takeIf { it.isNotBlank() },
            merchantNormalized = merchantNormalized,
            accountTail = null,
            categoryId = categoryId,
            occurredAt = occurredAt,
            smsId = null,
            smsBodyHash = "manual:${UUID.randomUUID()}",
            smsBody = null,
            sender = null,
            notes = notes?.takeIf { it.isNotBlank() },
            isUserEdited = true,
            createdAt = now,
            updatedAt = now,
        )
        return transactionDao.insert(entity)
    }

    companion object {
        // User-learned rules beat seeded rules (priority 100), since rules are sorted ASC.
        private const val USER_RULE_PRIORITY = 50
    }
}
