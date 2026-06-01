package com.kaizenll.xpendiq.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaizenll.xpendiq.data.entity.IgnoredSender
import kotlinx.coroutines.flow.Flow

@Dao
interface IgnoredSenderDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sender: IgnoredSender)

    @Query("DELETE FROM ignored_senders WHERE senderId = :senderId")
    suspend fun delete(senderId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM ignored_senders WHERE senderId = :senderId)")
    suspend fun isIgnored(senderId: String): Boolean

    @Query("SELECT * FROM ignored_senders ORDER BY senderId ASC")
    fun observeAll(): Flow<List<IgnoredSender>>
}
