package com.example.spendy.parser.extractor

import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionType
import com.example.spendy.parser.Extractor
import com.example.spendy.parser.ParseConfidence
import com.example.spendy.parser.ParseUtil
import com.example.spendy.parser.ParsedTransaction

object WalletDebitExtractor : Extractor {
    override val name = "wallet_debit"

    // Payment of Rs 210.00 using Apay balance is successful at A.in. Updated balance is Rs 2163.72.
    private val apayRegex = Regex(
        "Payment of\\s+Rs\\s?([\\d,]+\\.?\\d*)\\s+using\\s+(Apay|Paytm|Mobikwik)\\s+balance is successful\\s+at\\s+([A-Za-z0-9 .]+?)\\.",
        setOf(RegexOption.IGNORE_CASE),
    )

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        val m = apayRegex.find(body) ?: return null
        val amount = ParseUtil.parseAmountPaise(m.groupValues[1]) ?: return null
        val merchant = m.groupValues[3].trim()
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
