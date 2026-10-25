package com.rhys.chat.data

import com.example.network.ApiClient
import com.example.network.ChatErrorMapper
import com.example.network.api.ChatApi
import com.example.network.api.NotificationApi
import com.example.network.bean.ChatConversationDto
import com.example.network.bean.ChatMessageDto
import com.example.network.bean.ChatNotificationDto
import com.example.network.bean.CursorPage
import com.example.network.bean.OpenChatConversationBody
import com.example.network.bean.SendChatMessageBody
import kotlinx.coroutines.CancellationException

/**
 * 基于现有 [ApiClient] 的远程聊天数据源。
 *
 * 只负责参数归一化、接口调用与错误转换；好友权限、会话可见性、消息
 * 幂等与最终内容校验由服务端负责。构造参数可注入 API 以便测试。
 */
class RemoteChatDataSource(
    private val chatApi: ChatApi = ApiClient.chatApi,
    private val notificationApi: NotificationApi = ApiClient.notificationApi
) : ChatDataSource {

    override val isMock: Boolean = false

    override suspend fun listConversations(
        limit: Int,
        cursor: String?
    ): CursorPage<ChatConversationDto> = callChatApi {
        chatApi.listConversations(
            limit = ChatDataSource.normalizedLimit(limit),
            cursor = ChatDataSource.normalizedCursor(cursor)
        )
    }

    override suspend fun openConversation(peerUserId: Long): ChatConversationDto {
        require(peerUserId > 0) { "peerUserId must be positive" }
        return callChatApi {
            chatApi.openConversation(OpenChatConversationBody(peerUserId))
        }
    }

    override suspend fun listMessages(
        conversationId: Long,
        limit: Int,
        before: String?
    ): CursorPage<ChatMessageDto> {
        require(conversationId > 0) { "conversationId must be positive" }
        return callChatApi {
            chatApi.listMessages(
                conversationId = conversationId,
                limit = ChatDataSource.normalizedLimit(limit),
                before = ChatDataSource.normalizedCursor(before)
            )
        }
    }

    override suspend fun sendMessage(
        conversationId: Long,
        clientMessageId: String,
        content: String
    ): ChatMessageDto {
        require(conversationId > 0) { "conversationId must be positive" }
        val safeClientMessageId = ChatDataSource.normalizedClientMessageId(clientMessageId)
        val safeContent = ChatDataSource.normalizedContent(content)
        return callChatApi {
            chatApi.sendMessage(
                conversationId = conversationId,
                body = SendChatMessageBody(
                    clientMessageId = safeClientMessageId,
                    content = safeContent
                )
            )
        }
    }

    override suspend fun listNotifications(
        limit: Int,
        cursor: String?
    ): CursorPage<ChatNotificationDto> = callChatApi {
        notificationApi.listNotifications(
            limit = ChatDataSource.normalizedLimit(limit),
            cursor = ChatDataSource.normalizedCursor(cursor)
        )
    }

    private suspend fun <T> callChatApi(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ChatErrorMapper.map(e, unauthorizedMessage = UNAUTHORIZED_MESSAGE)
        }
    }

    private companion object {
        const val UNAUTHORIZED_MESSAGE = "登录已失效，请重新登录"
    }
}
