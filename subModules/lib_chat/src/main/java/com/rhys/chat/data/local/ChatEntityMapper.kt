package com.rhys.chat.data.local

import com.example.network.bean.ChatConversationDto
import com.example.network.bean.ChatMessageDto
import com.example.network.bean.ChatNotificationDto
import com.rhys.chat.data.ChatTime
import com.rhys.chat.data.crypto.ChatCipher
import com.rhys.chat.data.crypto.ChatDecryption
import com.rhys.chat.domain.ChatConversation
import com.rhys.chat.domain.ChatMessage
import com.rhys.chat.domain.ChatMessageStatus
import com.rhys.chat.domain.ChatNotification

/**
 * DTO / 领域模型与 Room 实体之间的转换。
 *
 * 加密在写入时完成、解密在读取时完成；本类不做线程切换，
 * 调用方（Repository）负责在 IO 线程执行。
 */
internal class ChatEntityMapper(
    private val cipher: ChatCipher
) {

    /**
     * 会话 DTO → 实体。
     *
     * 合并规则：
     * - peer/lastMessage/updatedAt 缺省时保留本地已有值，避免不完整 DTO 清空缓存。
     * - 最后消息按时间取新：本地最后消息时间更新时保留本地预览与时间，
     *   防止列表页旧 Mock/旧快照刷新把详情页刚发送的预览回退。
     * - updatedAt 取 existing / dto.updatedAt / 最后消息时间的最大值，raw 与胜出时间一致。
     * - 新时间 >= 旧时间时允许服务端更新内容；不触碰消息表。
     */
    fun conversationToEntity(
        ownerUserId: Long,
        dto: ChatConversationDto,
        existing: ChatConversationEntity? = null
    ): ChatConversationEntity {
        require(dto.id > 0) { "conversation id must be positive" }
        val last = dto.lastMessage
        val peer = dto.peer?.takeIf { it.id > 0 }
        val parsedUpdated = ChatTime.parseMillis(dto.updatedAt)
        val incomingLastMillis = ChatTime.parseMillis(last?.createdAt)
        val existingLastMillis = existing?.lastMessageAtMillis ?: 0L
        val existingUpdatedMillis = existing?.updatedAtMillis ?: 0L

        val peerUserId = peer?.id ?: existing?.peerUserId ?: 0L
        val nicknameCipher = peer?.nickname
            ?.takeIf { it.isNotBlank() }
            ?.let(cipher::encrypt)
            ?: existing?.peerNicknameCipher
        val avatarCipher = peer?.avatar
            ?.takeIf { it.isNotBlank() }
            ?.let(cipher::encrypt)
            ?: existing?.peerAvatarCipher

        // 本地已有更新时间的最后消息时保留本地值；同时间允许新数据覆盖内容。
        val keepExistingLast = existing != null &&
            existingLastMillis > 0L &&
            existingLastMillis > incomingLastMillis

        val previewCipher = if (keepExistingLast) {
            existing!!.lastMessagePreviewCipher
        } else {
            last?.content
                ?.takeIf { it.isNotBlank() }
                ?.let(cipher::encrypt)
                ?: existing?.lastMessagePreviewCipher
        }
        val lastAtRaw = if (keepExistingLast) {
            existing!!.lastMessageAtRaw
        } else {
            last?.createdAt?.takeIf { it.isNotBlank() }
                ?: existing?.lastMessageAtRaw
        }
        val lastMillis = if (keepExistingLast) {
            existing!!.lastMessageAtMillis
        } else {
            incomingLastMillis.takeIf { it > 0 }
                ?: existing?.lastMessageAtMillis
        }

        val updatedAtMillis = maxOf(
            parsedUpdated,
            existingUpdatedMillis,
            lastMillis ?: 0L
        )
        val updatedAtRaw = when {
            parsedUpdated > 0 && parsedUpdated >= updatedAtMillis ->
                dto.updatedAt?.takeIf { it.isNotBlank() }
                    ?: existing?.updatedAtRaw
            (lastMillis ?: 0L) > 0 && (lastMillis ?: 0L) >= updatedAtMillis ->
                lastAtRaw ?: existing?.updatedAtRaw
            else -> existing?.updatedAtRaw
                ?: dto.updatedAt?.takeIf { it.isNotBlank() }
        }

        return ChatConversationEntity(
            ownerUserId = ownerUserId,
            conversationId = dto.id,
            peerUserId = peerUserId,
            peerNicknameCipher = nicknameCipher,
            peerAvatarCipher = avatarCipher,
            lastMessagePreviewCipher = previewCipher,
            lastMessageAtMillis = lastMillis,
            lastMessageAtRaw = lastAtRaw,
            updatedAtMillis = updatedAtMillis,
            updatedAtRaw = updatedAtRaw
        )
    }

    fun conversationToDomain(entity: ChatConversationEntity): ChatConversation =
        ChatConversation(
            id = entity.conversationId,
            peerId = entity.peerUserId,
            peerNickname = decryptOrPlaceholder(entity.peerNicknameCipher),
            peerAvatar = decryptOrPlaceholder(entity.peerAvatarCipher),
            lastMessagePreview = decryptOrPlaceholder(entity.lastMessagePreviewCipher),
            updatedAt = entity.updatedAtMillis,
            updatedAtRaw = entity.updatedAtRaw
        )

    fun messageToEntity(
        ownerUserId: Long,
        dto: ChatMessageDto,
        clientMessageId: String,
        status: String,
        createdAtMillis: Long,
        createdAtRaw: String?
    ): ChatMessageEntity {
        require(dto.conversationId > 0) { "conversationId must be positive" }
        return ChatMessageEntity(
            ownerUserId = ownerUserId,
            clientMessageId = clientMessageId,
            serverId = dto.id.takeIf { it > 0 },
            conversationId = dto.conversationId,
            senderId = dto.senderId,
            receiverId = dto.receiverId,
            type = dto.type.ifBlank { ChatMessageDto.TYPE_TEXT },
            contentCipher = cipher.encrypt(dto.content),
            createdAtMillis = createdAtMillis,
            createdAtRaw = createdAtRaw,
            localStatus = status
        )
    }

    fun messageToEntity(ownerUserId: Long, message: ChatMessage): ChatMessageEntity =
        ChatMessageEntity(
            ownerUserId = ownerUserId,
            clientMessageId = message.clientMessageId,
            serverId = message.serverId,
            conversationId = message.conversationId,
            senderId = message.senderId,
            receiverId = message.receiverId,
            type = ChatMessageDto.TYPE_TEXT,
            contentCipher = cipher.encrypt(message.content),
            createdAtMillis = message.createdAt,
            createdAtRaw = message.createdAtRaw,
            localStatus = message.status.name
        )

    fun messageToDomain(entity: ChatMessageEntity): ChatMessage =
        ChatMessage(
            serverId = entity.serverId,
            clientMessageId = entity.clientMessageId,
            conversationId = entity.conversationId,
            senderId = entity.senderId,
            receiverId = entity.receiverId,
            content = decryptOrPlaceholder(entity.contentCipher)
                ?: ChatCipher.UNAVAILABLE_TEXT,
            createdAt = entity.createdAtMillis,
            createdAtRaw = entity.createdAtRaw,
            status = ChatMessageStatus.fromStored(entity.localStatus)
        )

    fun notificationToEntity(
        ownerUserId: Long,
        dto: ChatNotificationDto
    ): ChatNotificationEntity {
        require(dto.id > 0) { "notification id must be positive" }
        return ChatNotificationEntity(
            ownerUserId = ownerUserId,
            notificationId = dto.id,
            type = dto.type.ifBlank { ChatNotificationDto.TYPE_RESERVATION },
            titleCipher = cipher.encrypt(dto.title),
            bodyCipher = cipher.encrypt(dto.body),
            orderId = dto.orderId,
            createdAtMillis = ChatTime.parseMillis(dto.createdAt),
            createdAtRaw = dto.createdAt
        )
    }

    fun notificationToDomain(entity: ChatNotificationEntity): ChatNotification =
        ChatNotification(
            id = entity.notificationId,
            type = entity.type,
            title = decryptOrPlaceholder(entity.titleCipher)
                ?: ChatCipher.UNAVAILABLE_TEXT,
            body = decryptOrPlaceholder(entity.bodyCipher)
                ?: ChatCipher.UNAVAILABLE_TEXT,
            orderId = entity.orderId,
            createdAt = entity.createdAtMillis,
            createdAtRaw = entity.createdAtRaw
        )

    /**
     * 解密失败返回安全占位；null 输入返回 null（字段本身缺省）。
     */
    private fun decryptOrPlaceholder(cipherText: String?): String? {
        cipherText ?: return null
        return when (val result = cipher.decrypt(cipherText)) {
            is ChatDecryption.Success -> result.value
            ChatDecryption.Unavailable -> ChatCipher.UNAVAILABLE_TEXT
        }
    }
}
