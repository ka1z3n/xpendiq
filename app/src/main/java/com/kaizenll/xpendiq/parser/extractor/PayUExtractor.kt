package com.kaizenll.xpendiq.parser.extractor

import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.parser.Extractor
import com.kaizenll.xpendiq.parser.ParseConfidence
import com.kaizenll.xpendiq.parser.ParseUtil
import com.kaizenll.xpendiq.parser.ParsedTransaction

object PayUExtractor : Extractor {
    override val name = "payu_gateway"

    // Transaction No. 10000000003 for Rs. 89.00 done for FLASHPE FOODS PRIVATE LIMITED has succeeded Team PayU
    private val regex = Regex(
        "Transaction No\\.\\s+\\d+\\s+for\\s+Rs\\.?\\s?([\\d,]+\\.?\\d*)\\s+done for\\s+(.+?)\\s+has succeeded",
        setOf(RegexOption.IGNORE_CASE),
    )

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        val m = regex.find(body) ?: return null
        val amount = ParseUtil.parseAmountPaise(m.groupValues[1]) ?: return null
        val merchant = m.groupValues[2].trim()
        return ParsedTransaction(
            amountPaise = amount,
            type = TransactionType.DEBIT,
            paymentMode = PaymentMode.UPI,
            merchantRaw = merchant,
            merchantNormalized = ParseUtil.normalizeMerchant(merchant),
            accountTail = null,
            occurredAt = receivedAtMillis,
            confidence = ParseConfidence.HIGH,
            extractor = name,
        )
    }
}
