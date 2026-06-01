package com.kaizenll.xpendiq.parser

import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionType

enum class ParseConfidence { HIGH, LOW }

data class ParsedTransaction(
    val amountPaise: Long,
    val currency: String = "INR",
    val type: TransactionType,
    val paymentMode: PaymentMode,
    val merchantRaw: String?,
    val merchantNormalized: String?,
    val accountTail: String?,
    val occurredAt: Long,
    val confidence: ParseConfidence,
    val extractor: String,
)
