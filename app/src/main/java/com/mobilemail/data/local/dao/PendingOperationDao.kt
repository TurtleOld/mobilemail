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

    @Query("UPDATE pending_operations SET status = :status, attemptCount = :attemptCount, " +
        "lastError = :lastError, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, attemptCount: Int, lastError: String?, updatedAt: Long)

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_operations SET status = 'pending', updatedAt = :updatedAt WHERE status = 'running'")
    suspend fun resetRunning(updatedAt: Long)

    @Query("DELETE FROM pending_operations WHERE server = :server AND email = :email")
    suspend fun deleteByAccount(server: String, email: String)

    @Query("SELECT COUNT(*) FROM pending_operations WHERE status IN ('pending', 'failed')")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM pending_operations WHERE status = 'permanent_failed'")
    suspend fun getPermanentFailedCount(): Int

    @Query("SELECT COUNT(*) FROM pending_operations WHERE status IN ('pending', 'failed') AND server = :server AND email = :email")
    suspend fun getPendingCountForAccount(server: String, email: String): Int

    @Query("SELECT COUNT(*) FROM pending_operations WHERE status = 'failed' AND server = :server AND email = :email")
    suspend fun getFailedCountForAccount(server: String, email: String): Int

    @Query("SELECT COUNT(*) FROM pending_operations WHERE status = 'permanent_failed' AND server = :server AND email = :email")
    suspend fun getPermanentFailedCountForAccount(server: String, email: String): Int

    @Query("SELECT * FROM pending_operations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingOperationEntity>>

    @Query("SELECT * FROM pending_operations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PendingOperationEntity?

    @Query("SELECT * FROM pending_operations WHERE status IN ('failed', 'permanent_failed') AND server = :server AND email = :email")
    suspend fun getFailedOperations(server: String, email: String): List<PendingOperationEntity>

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_operations SET status = 'pending', lastError = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun retryById(id: Long, updatedAt: Long)

    @Query("UPDATE pending_operations SET status = 'pending', lastError = NULL, " +
        "updatedAt = :updatedAt WHERE status IN ('failed', 'permanent_failed') " +
        "AND server = :server AND email = :email")
    suspend fun retryAllFailed(server: String, email: String, updatedAt: Long)

    @Query("DELETE FROM pending_operations WHERE status IN ('failed', 'permanent_failed') " +
        "AND server = :server AND email = :email")
    suspend fun clearFailed(server: String, email: String)
}
