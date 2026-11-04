package com.mobilemail.data.local.dao

import androidx.room.*
import com.mobilemail.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE localAccountId = :localAccountId ORDER BY name ASC")
    fun getFoldersByAccount(localAccountId: String): Flow<List<FolderEntity>>
    
    @Query("SELECT * FROM folders WHERE localAccountId = :localAccountId")
    suspend fun getFoldersByAccountSync(localAccountId: String): List<FolderEntity>
    
    @Query("SELECT * FROM folders WHERE id = :folderId AND localAccountId = :localAccountId")
    suspend fun getFolderById(folderId: String, localAccountId: String): FolderEntity?

    @Query("UPDATE folders SET queryState = :queryState, syncedAt = :syncedAt WHERE id = :folderId AND localAccountId = :localAccountId")
    suspend fun updateFolderSyncState(folderId: String, localAccountId: String, queryState: String?, syncedAt: Long)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)
    
    @Update
    suspend fun updateFolder(folder: FolderEntity)
    
    @Delete
    suspend fun deleteFolder(folder: FolderEntity)
    
    @Query("DELETE FROM folders WHERE localAccountId = :localAccountId")
    suspend fun deleteFoldersByAccount(localAccountId: String)
}
