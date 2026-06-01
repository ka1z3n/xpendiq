package com.kaizenll.xpendiq.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index("occurredAt"),
        Index("categoryId"),
        Index("type"),
        Index(value = ["smsBodyHash"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    /** ISO-4217 currency code. "INR" by default; "USD" for foreign-currency subscriptions. */
    val currency: String = "INR",
    val type: TransactionType,
    val paymentMode: PaymentMode,
    val merchantRaw: String?,
    val merchantNormalized: String?,
    val accountTail: String?,
    val categoryId: Long,
    val occurredAt: Long,
    val smsId: Long?,
    val smsBodyHash: String,
    val smsBody: String?,
    val sender: String?,
    val notes: String?,
    val isUserEdited: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
