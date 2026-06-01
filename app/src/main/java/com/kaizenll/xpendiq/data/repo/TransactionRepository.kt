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
            type = finalType,
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
