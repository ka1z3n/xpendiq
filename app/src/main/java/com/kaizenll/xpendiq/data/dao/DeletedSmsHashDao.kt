package com.kaizenll.xpendiq.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaizenll.xpendiq.data.entity.DeletedSmsHash

@Dao
interface DeletedSmsHashDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(hash: DeletedSmsHash)

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_sms_hashes WHERE smsBodyHash = :hash)")
    suspend fun exists(hash: String): Boolean
}
