package com.kaizenll.xpendiq.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import com.kaizenll.xpendiq.data.entity.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: TransactionEntity): Long

    @Update
    suspend fun update(txn: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE smsBodyHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): TransactionEntity?

    @Query("SELECT * FROM transactions")
    suspend fun findAll(): List<TransactionEntity>

    @Query("UPDATE transactions SET smsBodyHash = :newHash WHERE id = :id")
    suspend fun updateHash(id: Long, newHash: String)

    // Secondary `id DESC` breaks ties: SMS dates parse to midnight, so same-day rows share an
    // occurredAt; falling back to insertion order (highest id = most recently added) keeps the
    // newest transaction at the top of its day group instead of the bottom.
    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY occurredAt DESC, id DESC")
    fun observeByType(type: TransactionType): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun observeRecent(type: TransactionType, limit: Int): Flow<List<TransactionEntity>>

    /** Pass -1L as the sentinel when nothing should be excluded. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE type = :type
          AND categoryId != :excludeCategoryId
        ORDER BY occurredAt DESC, id DESC
        """
    )
    fun observeByTypeExcludingCategory(
        type: TransactionType,
        excludeCategoryId: Long,
    ): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE type = :type
          AND categoryId = :uncategorizedId
        ORDER BY occurredAt DESC, id DESC
        """
    )
    fun observeUncategorized(type: TransactionType, uncategorizedId: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE type = :type
          AND categoryId = :uncategorizedId
        """
    )
    fun observeUncategorizedCount(type: TransactionType, uncategorizedId: Long): Flow<Int>

    @Query(
        """
        UPDATE transactions
        SET categoryId = :newCategoryId,
            isUserEdited = 1,
            updatedAt = :now
        WHERE merchantNormalized = :merchantNormalized
          AND type = :type
        """
    )
    suspend fun recategorizeByMerchantAndType(
        merchantNormalized: String,
        type: TransactionType,
        newCategoryId: Long,
        now: Long,
    ): Int

    /**
     * Cross-type variant: also updates the row's `type` to [newType]. Used when the user moves
     * an Uncategorized transaction into a category of a different type (e.g. a DEBIT row into
     * the Investment category).
     */
    @Query(
        """
        UPDATE transactions
        SET categoryId = :newCategoryId,
            type = :newType,
            isUserEdited = 1,
            updatedAt = :now
        WHERE merchantNormalized = :merchantNormalized
          AND type = :oldType
        """
    )
    suspend fun recategorizeByMerchantWithTypeChange(
        merchantNormalized: String,
        oldType: TransactionType,
        newType: TransactionType,
        newCategoryId: Long,
        now: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    /**
     * Used by seed migration to re-route existing transactions to a new/changed category
     * when their merchantNormalized substring-matches a seed rule. Preserves user edits.
     */
    @Query(
        """
        UPDATE transactions
        SET categoryId = :newCategoryId, updatedAt = :now
        WHERE merchantNormalized LIKE '%' || :pattern || '%'
          AND type = :type
          AND isUserEdited = 0
          AND categoryId != :newCategoryId
        """
    )
    suspend fun applySeedRecategorizeByPattern(
        pattern: String,
        type: TransactionType,
        newCategoryId: Long,
        now: Long,
    ): Int

    @Query(
        """
        UPDATE transactions
        SET categoryId = :newCategoryId, updatedAt = :now
        WHERE categoryId = :oldCategoryId
        """
    )
    suspend fun reassignCategory(oldCategoryId: Long, newCategoryId: Long, now: Long): Int

    /**
     * Sum in INR paise. INR rows count directly; foreign rows count their frozen [amountInrPaise]
     * (converted via the user's manual rate). Foreign rows with no conversion yet contribute 0,
     * so they stay out of totals until a rate is set.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE WHEN currency = 'INR' THEN amountPaise
                 WHEN amountInrPaise IS NOT NULL THEN amountInrPaise
                 ELSE 0 END
        ), 0) FROM transactions
        WHERE type = :type
          AND occurredAt BETWEEN :startMillis AND :endMillis
        """
    )
    fun observeTotal(type: TransactionType, startMillis: Long, endMillis: Long): Flow<Long>

    /** Same as [observeTotal] but excludes a category id. Pass -1L for none. */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE WHEN currency = 'INR' THEN amountPaise
                 WHEN amountInrPaise IS NOT NULL THEN amountInrPaise
                 ELSE 0 END
        ), 0) FROM transactions
        WHERE type = :type
          AND occurredAt BETWEEN :startMillis AND :endMillis
          AND categoryId != :excludeCategoryId
        """
    )
    fun observeTotalExcludingCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
        excludeCategoryId: Long,
    ): Flow<Long>

    /** Per-category totals in INR paise (foreign rows folded in via their frozen [amountInrPaise]). */
    @Query(
        """
        SELECT categoryId AS categoryId, SUM(
            CASE WHEN currency = 'INR' THEN amountPaise
                 WHEN amountInrPaise IS NOT NULL THEN amountInrPaise
                 ELSE 0 END
        ) AS totalPaise
        FROM transactions
        WHERE type = :type
          AND occurredAt BETWEEN :startMillis AND :endMillis
        GROUP BY categoryId
        HAVING totalPaise > 0
        ORDER BY totalPaise DESC
        """
    )
    fun observeTotalsByCategory(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<CategoryTotal>>

    /**
     * INR-only sum that drops categories flagged [Category.excludedFromTotals] (self-transfers,
     * CC-bill payments). Used for spend/received totals where internal money movement shouldn't count.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            CASE WHEN currency = 'INR' THEN amountPaise
                 WHEN amountInrPaise IS NOT NULL THEN amountInrPaise
                 ELSE 0 END
        ), 0) FROM transactions
        WHERE type = :type
          AND occurredAt BETWEEN :startMillis AND :endMillis
          AND categoryId NOT IN (SELECT id FROM categories WHERE excludedFromTotals = 1)
        """
    )
    fun observeTotalExcludingFlagged(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
    ): Flow<Long>

    /** Per-category totals as [observeTotalsByCategory], minus the [Category.excludedFromTotals] ones. */
    @Query(
        """
        SELECT categoryId AS categoryId, SUM(
            CASE WHEN currency = 'INR' THEN amountPaise
                 WHEN amountInrPaise IS NOT NULL THEN amountInrPaise
                 ELSE 0 END
        ) AS totalPaise
        FROM transactions
        WHERE type = :type
          AND occurredAt BETWEEN :startMillis AND :endMillis
          AND categoryId NOT IN (SELECT id FROM categories WHERE excludedFromTotals = 1)
        GROUP BY categoryId
        HAVING totalPaise > 0
        ORDER BY totalPaise DESC
        """
    )
    fun observeTotalsByCategoryExcludingFlagged(
        type: TransactionType,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<CategoryTotal>>

    /**
     * Recompute the frozen INR-equivalent for every row of [currency] using [rate]. Run when the
     * user sets or changes the manual rate, so existing foreign rows fold into totals immediately.
     */
    @Query(
        """
        UPDATE transactions
        SET amountInrPaise = CAST(ROUND(amountPaise * :rate) AS INTEGER), updatedAt = :now
        WHERE currency = :currency
        """
    )
    suspend fun recomputeInrForCurrency(currency: String, rate: Double, now: Long): Int
}
