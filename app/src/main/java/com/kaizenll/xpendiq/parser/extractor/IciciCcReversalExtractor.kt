package com.kaizenll.xpendiq.parser.extractor

import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.parser.Extractor
import com.kaizenll.xpendiq.parser.ParseConfidence
import com.kaizenll.xpendiq.parser.ParseUtil
import com.kaizenll.xpendiq.parser.ParsedTransaction

object IciciCcReversalExtractor : Extractor {
    override val name = "icici_cc_reversal"

    // Reversal of Rs 22.11 credited to ICICI Bank Credit Card XX5151 on 26-MAY-25.
    private val regex = Regex(
        "Reversal of\\s+Rs\\s?([\\d,]+\\.?\\d*)\\s+credited to\\s+(ICICI|HDFC|SBI|Axis)\\s+Bank Credit Card\\s+XX(\\d{4})\\s+on\\s+(\\d{2}-[A-Z]{3}-\\d{2,4})",
        setOf(RegexOption.IGNORE_CASE),
    )

    const val MERCHANT_MARKER = "__CARD_REVERSAL__"

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        val m = regex.find(body) ?: return null
        val amount = ParseUtil.parseAmountPaise(m.groupValues[1]) ?: return null
        return ParsedTransaction(
            amountPaise = amount,
            type = TransactionType.CREDIT,
            paymentMode = PaymentMode.CARD_CREDIT,
            merchantRaw = "Card Reversal",
            merchantNormalized = MERCHANT_MARKER,
            accountTail = m.groupValues[3],
            occurredAt = ParseUtil.parseOccurredAt(m.groupValues[4], receivedAtMillis),
            confidence = ParseConfidence.HIGH,
            extractor = name,
        )
    }
}
