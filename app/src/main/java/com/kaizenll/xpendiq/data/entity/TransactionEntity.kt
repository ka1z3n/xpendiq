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
    /**
     * For a foreign-currency row, the INR-equivalent in paise, frozen at capture using the
     * user's manual rate (Settings). Null for INR rows (use [amountPaise] directly) and for
     * foreign rows captured while no rate was set. Totals sum this for foreign rows.
     */
    val amountInrPaise: Long? = null,
    val type: TransactionType,
    /**
     * True for rows captured while the user was not entitled (trial expired, no subscription).
     * Locked rows are hidden from every list/total and from CSV export, but keep accumulating;
     * subscribing flips them all back to false. Always false for manual entries (those are only
     * possible while entitled).
     */
    val locked: Boolean = false,
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
