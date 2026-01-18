package com.mobilemail.data.local.dao

import androidx.room.*
import com.mobilemail.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE folderId = :folderId AND accountId = :accountId ORDER BY date DESC")
    fun getMessagesByFolder(folderId: String, accountId: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE folderId = :folderId AND accountId = :accountId ORDER BY date DESC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesByFolderPaged(folderId: String, accountId: String, limit: Int, offset: Int): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Update
    suspend fun updateMessage(message: MessageEntity)
    
    @Delete
    suspend fun deleteMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE folderId = :folderId AND accountId = :accountId")
    suspend fun deleteMessagesByFolder(folderId: String, accountId: String)
    
    @Query("DELETE FROM messages WHERE accountId = :accountId")
    suspend fun deleteMessagesByAccount(accountId: String)
    
    @Query("UPDATE messages SET isUnread = :isUnread WHERE id = :messageId")
    suspend fun updateReadStatus(messageId: String, isUnread: Boolean)
    
    @Query("UPDATE messages SET isStarred = :isStarred WHERE id = :messageId")
    suspend fun updateStarredStatus(messageId: String, isStarred: Boolean)
}
