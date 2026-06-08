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

    @Query("SELECT COUNT(*) FROM messages WHERE folderId = :folderId AND accountId = :accountId")
    suspend fun getMessageCountByFolder(folderId: String, accountId: String): Int

    @Query("SELECT MAX(syncedAt) FROM messages WHERE folderId = :folderId AND accountId = :accountId")
    suspend fun getLatestSyncedAtByFolder(folderId: String, accountId: String): Long?
    
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

    @Query(
        """
        SELECT messages.* FROM messages
        JOIN messages_fts ON messages_fts.messageId = messages.id
        WHERE messages_fts MATCH :matchQuery
            AND messages.accountId = :accountId
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
        accountId: String,
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
        WHERE accountId = :accountId
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
        accountId: String,
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
