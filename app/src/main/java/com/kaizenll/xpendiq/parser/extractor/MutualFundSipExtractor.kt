package com.kaizenll.xpendiq.parser.extractor

import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionType
import com.kaizenll.xpendiq.parser.Extractor
import com.kaizenll.xpendiq.parser.ParseConfidence
import com.kaizenll.xpendiq.parser.ParseUtil
import com.kaizenll.xpendiq.parser.ParsedTransaction

object MutualFundSipExtractor : Extractor {
    override val name = "mf_sip"

    // Your SIP Purchase in Folio 12345678/90 under HDFC BSE Sensex Index Fund-DP Growth for Rs. 19,999.00 has been processed at the NAV of 774.657 for 25.817 units and 23-Jun-2025
    private val hdfcSipRegex = Regex(
        "SIP Purchase\\s+in Folio\\s+[\\w/]+\\s+under\\s+(.+?)\\s+for\\s+Rs\\.?\\s?([\\d,]+\\.?\\d*).*?(\\d{2}-[A-Za-z]{3}-\\d{2,4})",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // Dear Investor, Purchase transaction in Folio No. 87654321 in Scheme : SBI Small Cap Fund Dir Growth for date 24-Apr-2026 for amount of INR 24,998.75 at NAV of 191.0425
    private val sbiMfRegex = Regex(
        "Purchase transaction in Folio No\\.\\s+\\d+\\s+in Scheme\\s*:\\s*(.+?)\\s+for date\\s+(\\d{2}-[A-Za-z]{3}-\\d{2,4})\\s+for amount of\\s+INR\\s?([\\d,]+\\.?\\d*)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        hdfcSipRegex.find(body)?.let { m ->
            val scheme = m.groupValues[1].trim()
            val amount = ParseUtil.parseAmountPaise(m.groupValues[2]) ?: return null
            return ParsedTransaction(
                amountPaise = amount,
                type = TransactionType.INVESTMENT,
                paymentMode = PaymentMode.AUTO_DEBIT,
                merchantRaw = scheme,
                merchantNormalized = ParseUtil.normalizeMerchant(scheme),
                accountTail = null,
                occurredAt = ParseUtil.parseOccurredAt(m.groupValues[3], receivedAtMillis),
                confidence = ParseConfidence.HIGH,
                extractor = "${name}_hdfc",
            )
        }
        sbiMfRegex.find(body)?.let { m ->
            val scheme = m.groupValues[1].trim()
            val amount = ParseUtil.parseAmountPaise(m.groupValues[3]) ?: return null
            return ParsedTransaction(
                amountPaise = amount,
                type = TransactionType.INVESTMENT,
                paymentMode = PaymentMode.AUTO_DEBIT,
                merchantRaw = scheme,
                merchantNormalized = ParseUtil.normalizeMerchant(scheme),
                accountTail = null,
                occurredAt = ParseUtil.parseOccurredAt(m.groupValues[2], receivedAtMillis),
                confidence = ParseConfidence.HIGH,
                extractor = "${name}_sbi",
            )
        }
        return null
    }
}
