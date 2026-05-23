package com.example.spendy.parser.extractor

import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionType
import com.example.spendy.parser.Extractor
import com.example.spendy.parser.ParseConfidence
import com.example.spendy.parser.ParseUtil
import com.example.spendy.parser.ParsedTransaction

object HdfcUpiDebitExtractor : Extractor {
    override val name = "hdfc_upi_debit"

    // Sent Rs.60.00
    // From HDFC Bank A/C *1234
    // To Ramesh Kumar
    // On 11/01/26
    // Ref 200000000000
    private val regex = Regex(
        "Sent\\s+Rs\\.?\\s?([\\d,]+\\.?\\d*)\\s+From\\s+HDFC Bank\\s+A/C\\s*\\*?(\\d{4})\\s+To\\s+(.+?)\\s+On\\s+(\\d{2}/\\d{2}/\\d{2,4})",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        val m = regex.find(body) ?: return null
        val amount = ParseUtil.parseAmountPaise(m.groupValues[1]) ?: return null
        val tail = m.groupValues[2]
        val merchant = m.groupValues[3].trim()
        val date = m.groupValues[4]
        return ParsedTransaction(
            amountPaise = amount,
            type = TransactionType.DEBIT,
            paymentMode = PaymentMode.UPI,
            merchantRaw = merchant,
            merchantNormalized = ParseUtil.normalizeMerchant(merchant),
            accountTail = tail,
            occurredAt = ParseUtil.parseOccurredAt(date, receivedAtMillis),
            confidence = ParseConfidence.HIGH,
            extractor = name,
        )
    }
}
