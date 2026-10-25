package com.rhys.chat.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * 会话缓存。
 *
 * 主键 `(ownerUserId, conversationId)` 保证不同登录账号互不可见。
 * 对方昵称/头像与最后消息预览为用户可见文本，必须存密文。
 */
@Entity(
    tableName = "chat_conversations",
    primaryKeys = ["ownerUserId", "conversationId"],
    indices = [Index(value = ["ownerUserId", "updatedAtMillis"])]
)
data class ChatConversationEntity(
    val ownerUserId: Long,
    val conversationId: Long,
    val peerUserId: Long,
    val peerNicknameCipher: String?,
    val peerAvatarCipher: String?,
    val lastMessagePreviewCipher: String?,
    val lastMessageAtMillis: Long?,
    val lastMessageAtRaw: String?,
    val updatedAtMillis: Long,
    val updatedAtRaw: String?
)

/**
 * 消息缓存。
 *
 * 主键 `(ownerUserId, clientMessageId)`：clientMessageId 是客户端幂等键，
 * 服务端未返回时使用稳定的 `server:<id>` 兜底，避免同一条消息重复入库。
 */
@Entity(
    tableName = "chat_messages",
    primaryKeys = ["ownerUserId", "clientMessageId"],
    indices = [
        Index(value = ["ownerUserId", "conversationId", "createdAtMillis"]),
        Index(value = ["ownerUserId", "serverId"])
    ]
)
data class ChatMessageEntity(
    val ownerUserId: Long,
    val clientMessageId: String,
    val serverId: Long?,
    val conversationId: Long,
    val senderId: Long,
    val receiverId: Long?,
    val type: String,
    val contentCipher: String,
    val createdAtMillis: Long,
    val createdAtRaw: String?,
    val localStatus: String
)

/** 预订通知缓存；标题与正文为密文。 */
@Entity(
    tableName = "chat_notifications",
    primaryKeys = ["ownerUserId", "notificationId"],
    indices = [Index(value = ["ownerUserId", "createdAtMillis"])]
)
data class ChatNotificationEntity(
    val ownerUserId: Long,
    val notificationId: Long,
    val type: String,
    val titleCipher: String,
    val bodyCipher: String,
    val orderId: Long?,
    val createdAtMillis: Long,
    val createdAtRaw: String?
)
