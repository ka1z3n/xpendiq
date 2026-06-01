package com.kaizenll.xpendiq.parser.extractor

import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.parser.Extractor
import com.kaizenll.xpendiq.parser.ParseConfidence
import com.kaizenll.xpendiq.parser.ParseUtil
import com.kaizenll.xpendiq.parser.ParsedTransaction

object SbiUpiDebitExtractor : Extractor {
    override val name = "sbi_upi_debit"

    // Dear UPI user A/C X1010 debited by 35.0 on date 06Aug25 trf to Anil Verma Refno 100000000001.
    // Dear UPI user A/C X1010 debited by 40000.00 on date 06Apr26 trf to RAJESH GUPTA Refno 100000000002
    // The merchant portion ends at "Refno"; the date is in compact format ddMMMyy.
    private val regex = Regex(
        "A/C\\s+X+(\\d{4,6})\\s+debited by\\s+([\\d,]+\\.?\\d*)\\s+on date\\s+(\\d{2}[A-Za-z]{3}\\d{2,4})\\s+trf to\\s+(.+?)\\s+Refno",
        setOf(RegexOption.IGNORE_CASE),
    )

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        val m = regex.find(body) ?: return null
        // Amount in this format is a bare rupee number (no Rs prefix), so multiply by 100.
        val amountPaise = ParseUtil.parseAmountPaise(m.groupValues[2]) ?: return null
        val merchant = m.groupValues[4].trim()
        val date = m.groupValues[3]  // "06Aug25"
        val normalizedDate = "${date.substring(0, 2)}-${date.substring(2, 5)}-${date.substring(5)}"
        return ParsedTransaction(
            amountPaise = amountPaise,
            type = TransactionType.DEBIT,
            paymentMode = PaymentMode.UPI,
            merchantRaw = merchant,
            merchantNormalized = ParseUtil.normalizeMerchant(merchant),
            accountTail = m.groupValues[1].takeLast(4),
            occurredAt = ParseUtil.parseOccurredAt(normalizedDate, receivedAtMillis),
            confidence = ParseConfidence.HIGH,
            extractor = name,
        )
    }
}
