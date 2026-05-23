package com.example.spendy.parser.extractor

import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionType
import com.example.spendy.parser.Extractor
import com.example.spendy.parser.ParseConfidence
import com.example.spendy.parser.ParseUtil
import com.example.spendy.parser.ParsedTransaction

/**
 * SBI NEFT credit notification — often salary, sometimes other inflows.
 * Payer is captured as the merchant; "SALARY" hint in body promotes the credit to Income.
 */
object SbiNeftCreditExtractor : Extractor {
    override val name = "sbi_neft_credit"

    // INR 1,23,456.00 credited to your A/c No XX1010 on 29/09/2025 through NEFT with UTR HDFCH00000000001 by ACME TECH PRIVATE LIMITED, INFO: BATCHID:0004 0001 SALARY-SBI
    private val regex = Regex(
        "INR\\s?([\\d,]+\\.?\\d*)\\s+credited to your\\s+A/c\\s+No\\s+XX(\\d{4})\\s+on\\s+(\\d{2}/\\d{2}/\\d{2,4})\\s+through\\s+(NEFT|IMPS|RTGS).*?\\bby\\s+(.+?),",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val salaryHint = Regex("\\bSALARY\\b", RegexOption.IGNORE_CASE)

    /**
     * Marker placed on `merchantNormalized` when the SMS includes a SALARY hint, so the
     * Categorizer can auto-route to Income.
     */
    const val INCOME_MARKER = "__SALARY_NEFT__"

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        val m = regex.find(body) ?: return null
        val amount = ParseUtil.parseAmountPaise(m.groupValues[1]) ?: return null
        val payer = m.groupValues[5].trim()
        val isSalary = salaryHint.containsMatchIn(body)
        return ParsedTransaction(
            amountPaise = amount,
            type = TransactionType.CREDIT,
            paymentMode = PaymentMode.NETBANKING,
            merchantRaw = payer,
            merchantNormalized = if (isSalary) INCOME_MARKER else ParseUtil.normalizeMerchant(payer),
            accountTail = m.groupValues[2],
            occurredAt = ParseUtil.parseOccurredAt(m.groupValues[3], receivedAtMillis),
            confidence = ParseConfidence.HIGH,
            extractor = name,
        )
    }
}
