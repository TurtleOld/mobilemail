package com.mobilemail.data.local.dao

import androidx.room.*
import com.mobilemail.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE accountId = :accountId ORDER BY name ASC")
    fun getFoldersByAccount(accountId: String): Flow<List<FolderEntity>>
    
    @Query("SELECT * FROM folders WHERE accountId = :accountId")
    suspend fun getFoldersByAccountSync(accountId: String): List<FolderEntity>
    
    @Query("SELECT * FROM folders WHERE id = :folderId AND accountId = :accountId")
    suspend fun getFolderById(folderId: String, accountId: String): FolderEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)
    
    @Update
    suspend fun updateFolder(folder: FolderEntity)
    
    @Delete
    suspend fun deleteFolder(folder: FolderEntity)
    
    @Query("DELETE FROM folders WHERE accountId = :accountId")
    suspend fun deleteFoldersByAccount(accountId: String)
}
