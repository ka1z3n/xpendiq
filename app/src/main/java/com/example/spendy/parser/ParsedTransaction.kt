package com.example.spendy.parser

import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionType

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
