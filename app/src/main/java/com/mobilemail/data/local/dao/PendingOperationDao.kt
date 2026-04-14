package com.mobilemail.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mobilemail.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: PendingOperationEntity): Long

    @Query("SELECT * FROM pending_operations WHERE status IN ('pending', 'failed') ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getProcessable(limit: Int = 50): List<PendingOperationEntity>

    @Query("UPDATE pending_operations SET status = :status, attemptCount = :attemptCount, lastError = :lastError, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, attemptCount: Int, lastError: String?, updatedAt: Long)

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pending_operations WHERE status IN ('pending', 'failed')")
    suspend fun getPendingCount(): Int

    @Query("SELECT * FROM pending_operations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingOperationEntity>>

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun delete(id: Long)
}
