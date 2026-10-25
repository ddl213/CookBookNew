package com.rhys.chat.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 聊天本地缓存 DAO。
 *
 * 所有查询都必须显式带 `ownerUserId`，不得提供跨账号的读取入口；
 * 清空操作是唯一允许跨 owner 的写操作，用于退出登录/切换账号。
 */
@Dao
abstract class ChatDao {

    @Upsert
    abstract suspend fun upsertConversation(entity: ChatConversationEntity)

    @Upsert
    abstract suspend fun upsertConversations(entities: List<ChatConversationEntity>)

    @Upsert
    abstract suspend fun upsertMessage(entity: ChatMessageEntity)

    @Upsert
    abstract suspend fun upsertMessages(entities: List<ChatMessageEntity>)

    @Upsert
    abstract suspend fun upsertNotification(entity: ChatNotificationEntity)

    @Upsert
    abstract suspend fun upsertNotifications(entities: List<ChatNotificationEntity>)

    @Query(
        "SELECT * FROM chat_conversations WHERE ownerUserId = :ownerUserId " +
            "ORDER BY updatedAtMillis DESC, conversationId DESC"
    )
    abstract fun observeConversations(ownerUserId: Long): Flow<List<ChatConversationEntity>>

    @Query(
        "SELECT * FROM chat_messages WHERE ownerUserId = :ownerUserId " +
            "AND conversationId = :conversationId " +
            "ORDER BY createdAtMillis ASC, clientMessageId ASC"
    )
    abstract fun observeMessages(
        ownerUserId: Long,
        conversationId: Long
    ): Flow<List<ChatMessageEntity>>

    @Query(
        "SELECT * FROM chat_notifications WHERE ownerUserId = :ownerUserId " +
            "ORDER BY createdAtMillis DESC, notificationId DESC"
    )
    abstract fun observeNotifications(ownerUserId: Long): Flow<List<ChatNotificationEntity>>

    @Query(
        "SELECT * FROM chat_conversations WHERE ownerUserId = :ownerUserId " +
            "AND conversationId = :conversationId LIMIT 1"
    )
    abstract suspend fun findConversation(
        ownerUserId: Long,
        conversationId: Long
    ): ChatConversationEntity?

    @Query(
        "SELECT * FROM chat_messages WHERE ownerUserId = :ownerUserId " +
            "AND clientMessageId = :clientMessageId LIMIT 1"
    )
    abstract suspend fun findMessage(
        ownerUserId: Long,
        clientMessageId: String
    ): ChatMessageEntity?

    @Query(
        "UPDATE chat_messages SET localStatus = :status, serverId = :serverId " +
            "WHERE ownerUserId = :ownerUserId AND clientMessageId = :clientMessageId"
    )
    abstract suspend fun updateMessageStatus(
        ownerUserId: Long,
        clientMessageId: String,
        status: String,
        serverId: Long?
    ): Int

    @Query(
        "UPDATE chat_conversations SET lastMessagePreviewCipher = :previewCipher, " +
            "lastMessageAtMillis = :atMillis, lastMessageAtRaw = :atRaw, " +
            "updatedAtMillis = :atMillis, updatedAtRaw = :atRaw " +
            "WHERE ownerUserId = :ownerUserId AND conversationId = :conversationId"
    )
    abstract suspend fun updateConversationPreview(
        ownerUserId: Long,
        conversationId: Long,
        previewCipher: String,
        atMillis: Long,
        atRaw: String?
    ): Int

    @Query("SELECT COUNT(*) FROM chat_messages WHERE ownerUserId = :ownerUserId")
    abstract suspend fun countMessages(ownerUserId: Long): Int

    @Query("SELECT COUNT(*) FROM chat_conversations WHERE ownerUserId = :ownerUserId")
    abstract suspend fun countConversations(ownerUserId: Long): Int

    @Query("SELECT COUNT(*) FROM chat_notifications WHERE ownerUserId = :ownerUserId")
    abstract suspend fun countNotifications(ownerUserId: Long): Int

    @Query("DELETE FROM chat_messages")
    abstract suspend fun deleteAllMessages()

    @Query("DELETE FROM chat_conversations")
    abstract suspend fun deleteAllConversations()

    @Query("DELETE FROM chat_notifications")
    abstract suspend fun deleteAllNotifications()

    /** 退出登录或切换账号时清空全部聊天缓存（含所有 owner）。 */
    @Transaction
    open suspend fun clearAll() {
        deleteAllMessages()
        deleteAllConversations()
        deleteAllNotifications()
    }
}
