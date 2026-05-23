package com.example.spendy.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MerchantRuleSource { SEED, USER_LEARNED }

@Entity(
    tableName = "merchant_rules",
    indices = [Index("appliesToType"), Index("priority")],
)
data class MerchantRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val categoryId: Long,
    val appliesToType: TransactionType,
    val priority: Int,
    val source: MerchantRuleSource,
)
