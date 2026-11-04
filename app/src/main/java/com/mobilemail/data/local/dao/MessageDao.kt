package com.mobilemail.data.local.dao

import androidx.room.*
import com.mobilemail.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE folderId = :folderId AND localAccountId = :localAccountId ORDER BY date DESC")
    fun getMessagesByFolder(folderId: String, localAccountId: String): Flow<List<MessageEntity>>
    
    @Query(
        """
        SELECT * FROM messages
        WHERE folderId = :folderId AND localAccountId = :localAccountId
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getMessagesByFolderPaged(folderId: String, localAccountId: String, limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE folderId = :folderId AND localAccountId = :localAccountId")
    suspend fun getMessageCountByFolder(folderId: String, localAccountId: String): Int

    @Query("SELECT MAX(syncedAt) FROM messages WHERE folderId = :folderId AND localAccountId = :localAccountId")
    suspend fun getLatestSyncedAtByFolder(folderId: String, localAccountId: String): Long?
    
    @Query("SELECT * FROM messages WHERE id = :messageId AND localAccountId = :localAccountId")
    suspend fun getMessageById(messageId: String, localAccountId: String): MessageEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Update
    suspend fun updateMessage(message: MessageEntity)
    
    @Delete
    suspend fun deleteMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE folderId = :folderId AND localAccountId = :localAccountId")
    suspend fun deleteMessagesByFolder(folderId: String, localAccountId: String)
    
    @Query("DELETE FROM messages WHERE localAccountId = :localAccountId")
    suspend fun deleteMessagesByAccount(localAccountId: String)
    
    @Query("UPDATE messages SET isUnread = :isUnread WHERE id = :messageId AND localAccountId = :localAccountId")
    suspend fun updateReadStatus(messageId: String, localAccountId: String, isUnread: Boolean)
    
    @Query("UPDATE messages SET isStarred = :isStarred WHERE id = :messageId AND localAccountId = :localAccountId")
    suspend fun updateStarredStatus(messageId: String, localAccountId: String, isStarred: Boolean)

    @Query(
        """
        SELECT messages.* FROM messages
        JOIN messages_fts
            ON messages_fts.messageId = messages.id
            AND messages_fts.localAccountId = messages.localAccountId
        WHERE messages_fts MATCH :matchQuery
            AND messages.localAccountId = :localAccountId
            AND (:folderId IS NULL OR messages.folderId = :folderId)
            AND (:senderLike IS NULL OR LOWER(IFNULL(messages.fromName, '')) LIKE :senderLike OR LOWER(messages.fromEmail) LIKE :senderLike)
            AND (:unreadOnly = 0 OR messages.isUnread = 1)
            AND (:hasAttachments = 0 OR messages.hasAttachments = 1)
            AND (:starredOnly = 0 OR messages.isStarred = 1)
            AND (:importantOnly = 0 OR messages.isImportant = 1)
            AND (:dateFromMillis IS NULL OR messages.date >= :dateFromMillis)
        ORDER BY messages.date DESC
        LIMIT :limit OFFSET :offset
        """
    )
    @Suppress("LongParameterList")
    suspend fun searchMessagesFts(
        localAccountId: String,
        matchQuery: String,
        folderId: String?,
        senderLike: String?,
        unreadOnly: Boolean,
        hasAttachments: Boolean,
        starredOnly: Boolean,
        importantOnly: Boolean,
        dateFromMillis: Long?,
        limit: Int,
        offset: Int
    ): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE localAccountId = :localAccountId
            AND (:folderId IS NULL OR folderId = :folderId)
            AND (:senderLike IS NULL OR LOWER(IFNULL(fromName, '')) LIKE :senderLike OR LOWER(fromEmail) LIKE :senderLike)
            AND (:unreadOnly = 0 OR isUnread = 1)
            AND (:hasAttachments = 0 OR hasAttachments = 1)
            AND (:starredOnly = 0 OR isStarred = 1)
            AND (:importantOnly = 0 OR isImportant = 1)
            AND (:dateFromMillis IS NULL OR date >= :dateFromMillis)
        ORDER BY date DESC
        LIMIT :limit OFFSET :offset
        """
    )
    @Suppress("LongParameterList")
    suspend fun searchMessagesFiltered(
        localAccountId: String,
        folderId: String?,
        senderLike: String?,
        unreadOnly: Boolean,
        hasAttachments: Boolean,
        starredOnly: Boolean,
        importantOnly: Boolean,
        dateFromMillis: Long?,
        limit: Int,
        offset: Int
    ): List<MessageEntity>
}
