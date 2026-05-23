package com.example.spendy.parser.extractor

import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionType
import com.example.spendy.parser.Extractor
import com.example.spendy.parser.ParseConfidence
import com.example.spendy.parser.ParseUtil
import com.example.spendy.parser.ParsedTransaction

object GenericFallbackExtractor : Extractor {
    override val name = "generic_fallback"

    // Strong unambiguous verbs only. "paid" and "purchase" appear in marketing/shipping SMS
    // ("you've already paid", "purchase confirmed") and produce false positives.
    private val debitVerb = Regex("\\b(debited|spent|withdrawn|sent rs)\\b", RegexOption.IGNORE_CASE)
    private val creditVerb = Regex("\\b(credited|salary credited|reversal of|refund of)\\b", RegexOption.IGNORE_CASE)

    // Require some bank/payment context to anchor low-confidence parses.
    private val bankContext = Regex(
        "\\b(a/?c|account|card|upi|net\\s*banking|imps|neft|rtgs|transaction|txn)\\b",
        RegexOption.IGNORE_CASE,
    )

    override fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction? {
        val (amount, currency) = ParseUtil.findAmountWithCurrency(body) ?: return null
        if (!bankContext.containsMatchIn(body)) return null
        val type = when {
            debitVerb.containsMatchIn(body) -> TransactionType.DEBIT
            creditVerb.containsMatchIn(body) -> TransactionType.CREDIT
            else -> return null
        }
        return ParsedTransaction(
            amountPaise = amount,
            currency = currency,
            type = type,
            paymentMode = PaymentMode.UNKNOWN,
            merchantRaw = null,
            merchantNormalized = null,
            accountTail = null,
            occurredAt = receivedAtMillis,
            confidence = ParseConfidence.LOW,
            extractor = name,
        )
    }
}
