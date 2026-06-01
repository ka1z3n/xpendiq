package com.kaizenll.xpendiq.parser.extractor

import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.parser.Extractor
import com.kaizenll.xpendiq.parser.ParseConfidence
import com.kaizenll.xpendiq.parser.ParseUtil
import com.kaizenll.xpendiq.parser.ParsedTransaction

/**
 * FASTag toll deductions. Both ICICI ("Amazon Pay ICICI Bank FASTag") and IndusInd shapes.
 * Always categorized as transport via the FASTAG_MARKER token.
 */
object FastagTollExtractor : Extractor {
    override val name = "fastag_toll"
    const val MERCHANT_MARKER = "__FASTAG_TOLL__"

    // Rs.20 paid at TSPA for TS07AB1234 on 28-11-2025 20:11:34 with Amazon Pay ICICI Bank FASTag
    private val iciciRegex = Regex(
        "Rs\\.?\\s?([\\d,]+\\.?\\d*)\\s+paid at\\s+([A-Za-z0-9 .&_/-]+?)\\s+for\\s+[A-Z0-9]+\\s+on\\s+(\\d{2}-\\d{2}-\\d{4})\\b.*FASTag",
        setOf(RegexOption.IGNORE_CASE),
    )

    // Rs.50.00 toll paid at Shamshabad on 20-Dec-2025 08:56:08 PM for MP51XY5678 via FASTag. Bal Rs.260.00.
    private val indusRegex = Regex(
        "Rs\\.?\\s?([\\d,]+\\.?\\d*)\\s+toll paid at\\s+([A-Za-z0-9 .&_/-]+?)\\s+on\\s+(\\d{2}-[A-Za-z]{3}-\\d{4})\\b.*via FASTag",
        setOf(RegexOption.IGNORE_CASE),
    )

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        iciciRegex.find(body)?.let { m ->
            return build(m.groupValues[1], m.groupValues[2], m.groupValues[3], receivedAtMillis, "icici")
        }
        indusRegex.find(body)?.let { m ->
            return build(m.groupValues[1], m.groupValues[2], m.groupValues[3], receivedAtMillis, "indusind")
        }
        return null
    }

    private fun build(amountStr: String, location: String, date: String, receivedAt: Long, issuer: String): ParsedTransaction? {
        val amount = ParseUtil.parseAmountPaise(amountStr) ?: return null
        val locTrimmed = location.trim()
        return ParsedTransaction(
            amountPaise = amount,
            type = TransactionType.DEBIT,
            paymentMode = PaymentMode.AUTO_DEBIT,
            merchantRaw = "FASTag toll at $locTrimmed",
            merchantNormalized = MERCHANT_MARKER,
            accountTail = null,
            occurredAt = ParseUtil.parseOccurredAt(date, receivedAt),
            confidence = ParseConfidence.HIGH,
            extractor = "${name}_$issuer",
        )
    }
}
