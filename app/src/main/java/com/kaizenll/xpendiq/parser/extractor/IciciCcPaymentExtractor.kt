package com.kaizenll.xpendiq.parser.extractor

import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.parser.Extractor
import com.kaizenll.xpendiq.parser.ParseConfidence
import com.kaizenll.xpendiq.parser.ParseUtil
import com.kaizenll.xpendiq.parser.ParsedTransaction

/**
 * Credit-card BILL PAYMENT received — *not* income. Categorizer routes by merchantRaw == TRANSFER_CC.
 */
object IciciCcPaymentExtractor : Extractor {
    override val name = "icici_cc_bill_payment"

    // Payment of Rs 16,002.17 has been received on your ICICI Bank Credit Card XX5151 through Bharat Bill Payment System on 23-MAY-25.
    private val regex = Regex(
        "Payment of\\s+Rs\\s?([\\d,]+\\.?\\d*)\\s+has been received on your\\s+ICICI Bank Credit Card\\s+XX(\\d{4}).*?on\\s+(\\d{2}-[A-Z]{3}-\\d{2,4})",
        setOf(RegexOption.IGNORE_CASE),
    )

    const val MERCHANT_MARKER = "__CC_BILL_PAYMENT__"

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        val m = regex.find(body) ?: return null
        val amount = ParseUtil.parseAmountPaise(m.groupValues[1]) ?: return null
        return ParsedTransaction(
            amountPaise = amount,
            type = TransactionType.CREDIT,
            paymentMode = PaymentMode.CARD_CREDIT,
            merchantRaw = "Credit Card Bill Payment",
            merchantNormalized = MERCHANT_MARKER,
            accountTail = m.groupValues[2],
            occurredAt = ParseUtil.parseOccurredAt(m.groupValues[3], receivedAtMillis),
            confidence = ParseConfidence.HIGH,
            extractor = name,
        )
    }
}
