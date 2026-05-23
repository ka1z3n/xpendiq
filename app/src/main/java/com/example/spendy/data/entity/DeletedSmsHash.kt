package com.example.spendy.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_sms_hashes")
data class DeletedSmsHash(
    @PrimaryKey val smsBodyHash: String,
    val deletedAt: Long,
)
