package com.rhys.chat.data

import com.example.network.bean.ChatConversationDto
import com.example.network.bean.ChatMessageDto
import com.example.network.bean.ChatNotificationDto
import com.example.network.bean.CursorPage

/**
 * 聊天与预订通知的可替换数据源。
 *
 * 页面只依赖本接口，不直接依赖 Retrofit；Mock 与远程实现由
 * [ChatDataSourceConfig] 按构建配置选择。好友关系、会话参与方与消息
 * 幂等最终由服务端判定，客户端预校验不能替代权限控制。
 */
interface ChatDataSource {

    /** 是否为本地演示数据源；UI 必须据此明确标识演示模式，不得显示为真实同步。 */
    val isMock: Boolean

    suspend fun listConversations(
        limit: Int = DEFAULT_LIMIT,
        cursor: String? = null
    ): CursorPage<ChatConversationDto>

    /** 打开或创建与好友的会话；已存在时返回现有会话。 */
    suspend fun openConversation(peerUserId: Long): ChatConversationDto

    /** 历史消息按 createdAt 升序返回；before 为空时返回最新一页，nextCursor 指向更早一页。 */
    suspend fun listMessages(
        conversationId: Long,
        limit: Int = DEFAULT_LIMIT,
        before: String? = null
    ): CursorPage<ChatMessageDto>

    /** 发送文字消息；同一 clientMessageId 重复提交必须返回同一条消息。 */
    suspend fun sendMessage(
        conversationId: Long,
        clientMessageId: String,
        content: String
    ): ChatMessageDto

    suspend fun listNotifications(
        limit: Int = DEFAULT_LIMIT,
        cursor: String? = null
    ): CursorPage<ChatNotificationDto>

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
        const val MAX_MESSAGE_LENGTH = 2000

        /** limit 归一化到 1..50，避免客户端传入 0、负数或超大值。 */
        fun normalizedLimit(limit: Int): Int = limit.coerceIn(1, MAX_LIMIT)

        /** 空白游标视为第一页。 */
        fun normalizedCursor(cursor: String?): String? =
            cursor?.trim()?.takeIf { it.isNotEmpty() }

        /** 客户端幂等键预校验；服务端仍需按同一键做最终去重。 */
        fun normalizedClientMessageId(clientMessageId: String): String {
            val safe = clientMessageId.trim()
            require(safe.isNotEmpty()) { "clientMessageId must not be blank" }
            return safe
        }

        /** 文字内容 trim 后必须为 1..2000 字符；服务端仍是最终校验方。 */
        fun normalizedContent(content: String): String {
            val safe = content.trim()
            require(safe.isNotEmpty()) { "message content must not be blank" }
            require(safe.length <= MAX_MESSAGE_LENGTH) {
                "message content must not exceed $MAX_MESSAGE_LENGTH characters"
            }
            return safe
        }
    }
}
