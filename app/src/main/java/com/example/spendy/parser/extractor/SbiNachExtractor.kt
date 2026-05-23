package com.example.spendy.parser.extractor

import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionType
import com.example.spendy.parser.Extractor
import com.example.spendy.parser.ParseConfidence
import com.example.spendy.parser.ParseUtil
import com.example.spendy.parser.ParsedTransaction

object SbiNachExtractor : Extractor {
    override val name = "sbi_nach"

    // Your A/C XXXXX710000 has a debit by NACH of Rs 50,000.00 on 13/05/25
    private val debitRegex = Regex(
        "A/C\\s+X+(\\d{4,6})\\s+has a debit by NACH(?:-\\s*([^\\s][^\\d]*?))?\\s+of\\s+Rs\\.?\\s?([\\d,]+\\.?\\d*)\\s+on\\s+(\\d{2}/\\d{2}/\\d{2,4})",
        setOf(RegexOption.IGNORE_CASE),
    )

    // Your A/C XXXXX710000 has a credit by NACH- SAMPLECORP of Rs 150.40 on 01/08/25
    private val creditRegex = Regex(
        "A/C\\s+X+(\\d{4,6})\\s+has a credit by NACH-\\s*([^\\d]+?)\\s+of\\s+Rs\\.?\\s?([\\d,]+\\.?\\d*)\\s+on\\s+(\\d{2}/\\d{2}/\\d{2,4})",
        setOf(RegexOption.IGNORE_CASE),
    )

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        creditRegex.find(body)?.let { m ->
            val amount = ParseUtil.parseAmountPaise(m.groupValues[3]) ?: return null
            val payer = m.groupValues[2].trim()
            return ParsedTransaction(
                amountPaise = amount,
                type = TransactionType.CREDIT,
                paymentMode = PaymentMode.NETBANKING,
                merchantRaw = payer,
                merchantNormalized = ParseUtil.normalizeMerchant(payer),
                accountTail = m.groupValues[1].takeLast(4),
                occurredAt = ParseUtil.parseOccurredAt(m.groupValues[4], receivedAtMillis),
                confidence = ParseConfidence.HIGH,
                extractor = "${name}_credit",
            )
        }
        debitRegex.find(body)?.let { m ->
            val amount = ParseUtil.parseAmountPaise(m.groupValues[3]) ?: return null
            val payee = m.groupValues[2].trim().ifEmpty { "NACH debit" }
            return ParsedTransaction(
                amountPaise = amount,
                type = TransactionType.DEBIT,
                paymentMode = PaymentMode.AUTO_DEBIT,
                merchantRaw = payee,
                merchantNormalized = ParseUtil.normalizeMerchant(payee),
                accountTail = m.groupValues[1].takeLast(4),
                occurredAt = ParseUtil.parseOccurredAt(m.groupValues[4], receivedAtMillis),
                confidence = ParseConfidence.HIGH,
                extractor = "${name}_debit",
            )
        }
        return null
    }
}
