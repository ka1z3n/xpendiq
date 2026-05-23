package com.example.spendy.parser.extractor

import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionType
import com.example.spendy.parser.Extractor
import com.example.spendy.parser.ParseConfidence
import com.example.spendy.parser.ParseUtil
import com.example.spendy.parser.ParsedTransaction

object HdfcCreditAlertExtractor : Extractor {
    override val name = "hdfc_credit_alert"

    // Credit Alert!
    // Rs.100.00 credited to HDFC Bank A/c XX1234 on 19-11-25 from VPA john.doe@oksbi (UPI 100000000000)
    private val vpaRegex = Regex(
        "Rs\\.?\\s?([\\d,]+\\.?\\d*)\\s+credited to\\s+HDFC Bank\\s+A/c\\s+XX(\\d{4})\\s+on\\s+(\\d{2}-\\d{2}-\\d{2,4})\\s+from\\s+VPA\\s+([^\\s()]+)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    // Update! INR 25,000.00 deposited in HDFC Bank A/c XX1234 on 19-DEC-25 for CHQ DEP...
    private val depositRegex = Regex(
        "INR\\s?([\\d,]+\\.?\\d*)\\s+deposited\\s+in\\s+HDFC Bank\\s+A/c\\s+XX(\\d{4})\\s+on\\s+(\\d{2}-[A-Z]{3}-\\d{2,4})\\s+for\\s+([^.]+)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        vpaRegex.find(body)?.let { m ->
            val amount = ParseUtil.parseAmountPaise(m.groupValues[1]) ?: return null
            return ParsedTransaction(
                amountPaise = amount,
                type = TransactionType.CREDIT,
                paymentMode = PaymentMode.UPI,
                merchantRaw = m.groupValues[4],
                merchantNormalized = ParseUtil.normalizeMerchant(m.groupValues[4]),
                accountTail = m.groupValues[2],
                occurredAt = ParseUtil.parseOccurredAt(m.groupValues[3], receivedAtMillis),
                confidence = ParseConfidence.HIGH,
                extractor = name,
            )
        }
        depositRegex.find(body)?.let { m ->
            val amount = ParseUtil.parseAmountPaise(m.groupValues[1]) ?: return null
            return ParsedTransaction(
                amountPaise = amount,
                type = TransactionType.CREDIT,
                paymentMode = PaymentMode.NETBANKING,
                merchantRaw = m.groupValues[4].trim(),
                merchantNormalized = ParseUtil.normalizeMerchant(m.groupValues[4]),
                accountTail = m.groupValues[2],
                occurredAt = ParseUtil.parseOccurredAt(m.groupValues[3], receivedAtMillis),
                confidence = ParseConfidence.HIGH,
                extractor = "${name}_deposit",
            )
        }
        return null
    }
}
