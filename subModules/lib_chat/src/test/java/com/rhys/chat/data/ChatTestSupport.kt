package com.rhys.chat.data

import com.example.network.bean.ChatConversationDto
import com.example.network.bean.ChatMessageDto
import com.example.network.bean.ChatNotificationDto
import com.example.network.bean.CursorPage
import com.example.network.bean.UserDto
import com.rhys.chat.data.crypto.AesGcmChatCipher
import com.rhys.chat.data.crypto.ChatCipher
import com.rhys.chat.data.session.ChatOwnerStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.io.IOException
import javax.crypto.KeyGenerator

internal const val TEST_CONVERSATION_ID = 101L
internal const val TEST_PEER_ID = 2L
internal const val TEST_PEER_NICKNAME = "林小厨"
internal const val TEST_PEER_AVATAR = "https://cdn.example.com/avatar.png"
internal const val TEST_MESSAGE_ONE = "你好，看到你做的红烧肉了，看起来很棒！"
internal const val TEST_MESSAGE_TWO = "谢谢！周末有空可以来尝尝。"
internal const val TEST_NOTIFICATION_TITLE = "新的预订请求"
internal const val TEST_NOTIFICATION_BODY = "好友想预订你的菜谱"

/** 测试用 JVM AES-256-GCM 加密，不依赖 Android Keystore。 */
internal fun newTestCipher(): ChatCipher {
    val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    return AesGcmChatCipher { key }
}

/**
 * 可注入失败、延迟与分页的可控数据源；不访问网络。
 */
internal class FakeChatDataSource(
    override val isMock: Boolean = true,
    initialMessages: List<ChatMessageDto> = defaultMessages(),
    initialNotifications: List<ChatNotificationDto> = defaultNotifications()
) : ChatDataSource {

    val conversations = mutableListOf(defaultConversation())
    val messages = initialMessages.toMutableList()
    val notifications = initialNotifications.toMutableList()

    var failSend: Boolean = false
    var throwCancellationOnSend: Boolean = false
    var sendDelayMillis: Long = 0L
    var messagePageSize: Int = 50

    /** 用于模拟“数据源挂起期间切换账号”的并发测试。 */
    var messagesStarted: CompletableDeferred<Unit>? = null
    var messagesGate: CompletableDeferred<Unit>? = null
    var sendStarted: CompletableDeferred<Unit>? = null
    var sendGate: CompletableDeferred<Unit>? = null

    val sentClientMessageIds = mutableListOf<String>()
    val sentContents = mutableListOf<String>()

    private var nextId = 900L

    override suspend fun listConversations(
        limit: Int,
        cursor: String?
    ): CursorPage<ChatConversationDto> {
        val sorted = conversations.sortedWith(
            compareByDescending<ChatConversationDto> { it.updatedAt.orEmpty() }
                .thenByDescending { it.id }
        )
        return CursorPage(items = sorted, nextCursor = null)
    }

    override suspend fun openConversation(peerUserId: Long): ChatConversationDto {
        require(peerUserId > 0) { "peerUserId must be positive" }
        conversations.firstOrNull { it.peer?.id == peerUserId }?.let { return it }
        val created = ChatConversationDto(
            id = nextId++,
            peer = UserDto(
                id = peerUserId,
                username = "user$peerUserId",
                nickname = "好友$peerUserId"
            )
        )
        conversations += created
        return created
    }

    override suspend fun listMessages(
        conversationId: Long,
        limit: Int,
        before: String?
    ): CursorPage<ChatMessageDto> {
        messagesStarted?.complete(Unit)
        messagesGate?.await()
        val sorted = messages
            .filter { it.conversationId == conversationId }
            .sortedWith(
                compareBy<ChatMessageDto> { it.createdAt.orEmpty() }.thenBy { it.id }
            )
        val pageSize = messagePageSize.coerceAtLeast(1)
        val rawEnd = when {
            before == null -> sorted.size
            before.startsWith(CURSOR_PREFIX) -> {
                before.removePrefix(CURSOR_PREFIX).toIntOrNull() ?: sorted.size
            }
            else -> sorted.size
        }
        val endExclusive = rawEnd.coerceIn(0, sorted.size)
        val startInclusive = (endExclusive - pageSize).coerceAtLeast(0)
        val page = sorted.subList(startInclusive, endExclusive)
        val nextCursor = if (startInclusive > 0) {
            "$CURSOR_PREFIX$startInclusive"
        } else {
            null
        }
        return CursorPage(items = page.toList(), nextCursor = nextCursor)
    }

    override suspend fun sendMessage(
        conversationId: Long,
        clientMessageId: String,
        content: String
    ): ChatMessageDto {
        sentClientMessageIds += clientMessageId
        sentContents += content
        if (sendDelayMillis > 0) delay(sendDelayMillis)
        if (throwCancellationOnSend) throw CancellationException("模拟协程取消")
        if (failSend) throw IOException("模拟网络失败")
        sendStarted?.complete(Unit)
        sendGate?.await()

        messages.firstOrNull { it.clientMessageId == clientMessageId }?.let {
            return it
        }
        val created = ChatMessageDto(
            id = nextId++,
            conversationId = conversationId,
            clientMessageId = clientMessageId,
            senderId = 1L,
            receiverId = TEST_PEER_ID,
            type = ChatMessageDto.TYPE_TEXT,
            content = content,
            createdAt = "2026-10-25T12:00:00Z"
        )
        messages += created
        return created
    }

    override suspend fun listNotifications(
        limit: Int,
        cursor: String?
    ): CursorPage<ChatNotificationDto> {
        val sorted = notifications.sortedWith(
            compareByDescending<ChatNotificationDto> { it.createdAt.orEmpty() }
                .thenByDescending { it.id }
        )
        return CursorPage(items = sorted, nextCursor = null)
    }

    companion object {
        private const val CURSOR_PREFIX = "index:"

        fun defaultConversation() = ChatConversationDto(
            id = TEST_CONVERSATION_ID,
            peer = UserDto(
                id = TEST_PEER_ID,
                username = "lin_xiaochu",
                nickname = TEST_PEER_NICKNAME,
                avatar = TEST_PEER_AVATAR
            ),
            lastMessage = defaultMessages().last(),
            updatedAt = "2026-10-20T09:01:00Z"
        )

        fun defaultMessages(): List<ChatMessageDto> = listOf(
            ChatMessageDto(
                id = 1L,
                conversationId = TEST_CONVERSATION_ID,
                clientMessageId = "remote-1",
                senderId = TEST_PEER_ID,
                receiverId = 1L,
                type = ChatMessageDto.TYPE_TEXT,
                content = TEST_MESSAGE_ONE,
                createdAt = "2026-10-20T09:00:00Z"
            ),
            ChatMessageDto(
                id = 2L,
                conversationId = TEST_CONVERSATION_ID,
                clientMessageId = "remote-2",
                senderId = 1L,
                receiverId = TEST_PEER_ID,
                type = ChatMessageDto.TYPE_TEXT,
                content = TEST_MESSAGE_TWO,
                createdAt = "2026-10-20T09:01:00Z"
            )
        )

        fun defaultNotifications(): List<ChatNotificationDto> = listOf(
            ChatNotificationDto(
                id = 1L,
                type = ChatNotificationDto.TYPE_RESERVATION,
                title = TEST_NOTIFICATION_TITLE,
                body = TEST_NOTIFICATION_BODY,
                orderId = null,
                createdAt = "2026-10-20T09:10:00Z"
            )
        )
    }
}

internal class FakeOwnerStore(initialOwnerId: Long? = null) : ChatOwnerStore {

    var ownerId: Long? = initialOwnerId

    override fun readOwnerId(): Long? = ownerId

    override fun writeOwnerId(ownerId: Long) {
        this.ownerId = ownerId
    }

    override fun clearOwnerId() {
        ownerId = null
    }
}
