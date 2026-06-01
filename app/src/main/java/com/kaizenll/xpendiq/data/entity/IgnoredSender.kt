package com.kaizenll.xpendiq.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ignored_senders")
data class IgnoredSender(
    @PrimaryKey val senderId: String,
    val addedAt: Long,
)
