package com.rhys.chat.domain

/**
 * 解密后的聊天领域模型。
 *
 * 与 `lib_network` 的 DTO 分离：网络层只负责传输结构，领域层承载本地存储
 * 合并后的结果。所有用户可见文本在持久化前必须加密，读取时解密；
 * 解密失败以 [com.rhys.chat.data.crypto.ChatCipher.UNAVAILABLE_TEXT] 占位。
 */
data class ChatConversation(
    val id: Long,
    val peerId: Long,
    /** 对方昵称；服务端未提供时为 null。 */
    val peerNickname: String?,
    /** 对方头像 URL；服务端未提供时为 null。 */
    val peerAvatar: String?,
    /** 最近一条消息预览；无消息时为 null。 */
    val lastMessagePreview: String?,
    /** 最近更新时间（毫秒），用于本地排序。 */
    val updatedAt: Long,
    /** 服务端原始时间字符串，保留用于排查与展示原文。 */
    val updatedAtRaw: String?
)

data class ChatMessage(
    /** 服务端消息 ID；仅本地发送中或失败时可能为 null。 */
    val serverId: Long?,
    /** 客户端幂等键；重试时必须保持不变。 */
    val clientMessageId: String,
    val conversationId: Long,
    val senderId: Long,
    val receiverId: Long?,
    val content: String,
    val createdAt: Long,
    val createdAtRaw: String?,
    val status: ChatMessageStatus
)

/** 本地发送状态；不包含服务端已读/送达推断。 */
enum class ChatMessageStatus {
    SENDING,
    SENT,
    FAILED;

    companion object {
        /** 未知历史值按已发送处理，避免把服务端消息误报为失败。 */
        fun fromStored(value: String?): ChatMessageStatus =
            values().firstOrNull { it.name == value } ?: SENT
    }
}

/** 消息列表中的独立预订通知（本期不实现未读/已读）。 */
data class ChatNotification(
    val id: Long,
    val type: String,
    val title: String,
    val body: String,
    val orderId: Long?,
    val createdAt: Long,
    val createdAtRaw: String?
)
