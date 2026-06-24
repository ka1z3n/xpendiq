package com.kaizenll.xpendiq.ui.transactions

import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.data.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionListBuilderTest {

    // 2026-06-20 12:00 UTC-ish; exact value irrelevant, only that all rows share a day.
    private val day = 1_750_000_000_000L

    private fun txn(
        id: Long,
        amountPaise: Long,
        currency: String = "INR",
        amountInrPaise: Long? = null,
    ) = TransactionEntity(
        id = id,
        amountPaise = amountPaise,
        currency = currency,
        amountInrPaise = amountInrPaise,
        type = TransactionType.DEBIT,
        paymentMode = PaymentMode.UPI,
        merchantRaw = "M$id",
        merchantNormalized = "M$id",
        accountTail = null,
        categoryId = 1,
        occurredAt = day,
        smsId = null,
        smsBodyHash = "h$id",
        smsBody = null,
        sender = null,
        notes = null,
        isUserEdited = false,
        createdAt = day,
        updatedAt = day,
    )

    @Test fun `day total folds converted USD into INR, not its raw minor units`() {
        val rows = listOf(
            txn(1, amountPaise = 33_825),                                  // ₹338.25
            txn(2, amountPaise = 2_500, currency = "USD", amountInrPaise = 2_07_500), // $25 -> ₹2075
        )
        val sections = TransactionListBuilder.build(rows, categories = emptyList())
        assertEquals(1, sections.size)
        // ₹338.25 + ₹2075.00 = ₹2413.25, NOT ₹338.25 and NOT ₹363.25 (the old "+$25 as ₹25" bug).
        assertEquals(2_413_25L, sections.first().totalAmountPaise)
    }

    @Test fun `unconverted foreign row contributes zero to the day total`() {
        val rows = listOf(
            txn(1, amountPaise = 10_000),                       // ₹100
            txn(2, amountPaise = 999, currency = "USD"),        // $9.99, no rate set -> excluded
        )
        val sections = TransactionListBuilder.build(rows, categories = emptyList())
        assertEquals(10_000L, sections.first().totalAmountPaise)
    }
}
