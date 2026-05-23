package com.example.spendy.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.spendy.data.entity.Category
import com.example.spendy.data.entity.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories WHERE appliesToType = :type ORDER BY sortOrder ASC")
    fun observeByType(type: TransactionType): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE appliesToType = :type ORDER BY sortOrder ASC")
    suspend fun findByType(type: TransactionType): List<Category>

    @Query("SELECT * FROM categories")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE appliesToType = :type AND name = 'Uncategorized' AND isSystem = 1 LIMIT 1")
    suspend fun findUncategorized(type: TransactionType): Category?

    @Query("SELECT * FROM categories WHERE appliesToType = :type AND name = :name LIMIT 1")
    suspend fun findByNameAndType(name: String, type: TransactionType): Category?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}
