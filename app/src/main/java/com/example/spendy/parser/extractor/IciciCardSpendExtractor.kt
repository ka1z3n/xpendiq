package com.example.spendy.parser.extractor

import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionType
import com.example.spendy.parser.Extractor
import com.example.spendy.parser.ParseConfidence
import com.example.spendy.parser.ParseUtil
import com.example.spendy.parser.ParsedTransaction

object IciciCardSpendExtractor : Extractor {
    override val name = "icici_card_spend"

    // INR 508.00 spent using ICICI Bank Card XX5151 on 23-Jun-25 on INSTAMART. Avl Limit: INR 43,773.94.
    // INR 381.00 spent using ICICI Bank Card XX5151 on 24-Jun-25 on SWIGGY INSTAMAR. Avl Limit: INR 43,392.94.
    private val regex = Regex(
        "INR\\s?([\\d,]+\\.?\\d*)\\s+spent using\\s+ICICI Bank\\s+(?:Credit\\s+)?Card\\s+XX(\\d{4})\\s+on\\s+(\\d{2}-[A-Za-z]{3}-\\d{2,4})\\s+on\\s+([A-Z0-9 .&_/-]+?)\\.\\s*(?:Avl|If)",
        setOf(RegexOption.IGNORE_CASE),
    )

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        val m = regex.find(body) ?: return null
        val amount = ParseUtil.parseAmountPaise(m.groupValues[1]) ?: return null
        val merchant = m.groupValues[4].trim()
        // "ICICI Bank Card" (without "Credit") in the user's corpus is the credit card too;
        // treating as CARD_CREDIT until we see ICICI debit-card SMS with a different shape.
        return ParsedTransaction(
            amountPaise = amount,
            type = TransactionType.DEBIT,
            paymentMode = PaymentMode.CARD_CREDIT,
            merchantRaw = merchant,
            merchantNormalized = ParseUtil.normalizeMerchant(merchant),
            accountTail = m.groupValues[2],
            occurredAt = ParseUtil.parseOccurredAt(m.groupValues[3], receivedAtMillis),
            confidence = ParseConfidence.HIGH,
            extractor = name,
        )
    }
}
