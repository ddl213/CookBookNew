package com.example.network.bean

/**
 * 一对一文字消息，对应 chat/conversations/{id}/messages 的 items。
 *
 * 本期契约不包含已读/送达/发送状态字段；客户端不得自行推断服务端已读状态。
 * receiverId 可空以容忍服务端省略；createdAt 为 ISO-8601 字符串。
 */
data class ChatMessageDto(
    val id: Long = 0L,
    val conversationId: Long = 0L,
    val clientMessageId: String = "",
    val senderId: Long = 0L,
    val receiverId: Long? = null,
    val type: String = TYPE_TEXT,
    val content: String = "",
    val createdAt: String? = null
) {
    companion object {
        const val TYPE_TEXT = "TEXT"
    }
}

/**
 * 发送消息请求体。clientMessageId 用于服务端幂等：
 * 同一用户重复提交相同 clientMessageId 时必须返回同一条消息。
 */
data class SendChatMessageBody(
    val clientMessageId: String,
    val type: String = ChatMessageDto.TYPE_TEXT,
    val content: String
)

/** 打开/创建与好友的会话请求体。 */
data class OpenChatConversationBody(
    val peerUserId: Long
)
