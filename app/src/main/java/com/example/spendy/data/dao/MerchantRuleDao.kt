package com.example.spendy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.spendy.data.entity.MerchantRule
import com.example.spendy.data.entity.TransactionType

@Dao
interface MerchantRuleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<MerchantRule>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRule): Long

    @Query("SELECT * FROM merchant_rules WHERE appliesToType = :type ORDER BY priority ASC")
    suspend fun findForType(type: TransactionType): List<MerchantRule>

    @Query("SELECT EXISTS(SELECT 1 FROM merchant_rules WHERE pattern = :pattern AND appliesToType = :type)")
    suspend fun existsByPatternAndType(pattern: String, type: TransactionType): Boolean

    @Query("SELECT COUNT(*) FROM merchant_rules")
    suspend fun count(): Int

    @Query("DELETE FROM merchant_rules WHERE categoryId = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Long): Int
}
