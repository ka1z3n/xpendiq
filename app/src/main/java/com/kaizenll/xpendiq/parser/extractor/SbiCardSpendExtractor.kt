package com.kaizenll.xpendiq.parser.extractor

import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.parser.Extractor
import com.kaizenll.xpendiq.parser.ParseConfidence
import com.kaizenll.xpendiq.parser.ParseUtil
import com.kaizenll.xpendiq.parser.ParsedTransaction

object SbiCardSpendExtractor : Extractor {
    override val name = "card_spend"

    // Rs.1,268.50 spent on your SBI Credit Card ending 9999 at ATRIACONVERGENCETECH on 05/12/25.
    private val regex = Regex(
        "Rs\\.?\\s?([\\d,]+\\.?\\d*)\\s+spent on your\\s+(SBI|HDFC|ICICI|Axis|Kotak)\\s+Credit Card\\s+ending\\s+(\\d{4})\\s+at\\s+([A-Z0-9 ./_&-]+?)\\s+on\\s+(\\d{2}/\\d{2}/\\d{2,4})",
        setOf(RegexOption.IGNORE_CASE),
    )

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        val m = regex.find(body) ?: return null
        val amount = ParseUtil.parseAmountPaise(m.groupValues[1]) ?: return null
        val merchant = m.groupValues[4].trim()
        return ParsedTransaction(
            amountPaise = amount,
            type = TransactionType.DEBIT,
            paymentMode = PaymentMode.CARD_CREDIT,
            merchantRaw = merchant,
            merchantNormalized = ParseUtil.normalizeMerchant(merchant),
            accountTail = m.groupValues[3],
            occurredAt = ParseUtil.parseOccurredAt(m.groupValues[5], receivedAtMillis),
            confidence = ParseConfidence.HIGH,
            extractor = name,
        )
    }
}
