package com.rhys.chat.data

import com.example.network.AuthException
import com.example.network.bean.ChatConversationDto
import com.example.network.bean.ChatMessageDto
import com.example.network.bean.ChatNotificationDto
import com.example.network.bean.CursorPage
import com.example.network.bean.UserDto
import kotlinx.coroutines.delay
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Mock 数据源可模拟失败的调用类型。 */
enum class ChatMockOperation {
    CONVERSATIONS,
    OPEN_CONVERSATION,
    MESSAGES,
    SEND_MESSAGE,
    NOTIFICATIONS
}

/**
 * 注入给 [MockChatDataSource] 的失败行为。
 *
 * 仅用于本地开发与自动化验收，不会访问真实网络。
 */
data class ChatMockFailure(
    val operation: ChatMockOperation,
    val error: Throwable = IOException("模拟网络失败")
)

/**
 * 内存版聊天数据源，仅供本地开发与验收。
 *
 * [isMock] 恒为 true，页面必须明确标识演示模式；发送成功只代表内存/本地写入，
 * 不代表服务端同步成功。默认零延迟、无失败，可通过构造参数或
 * [setLatencyMillis]/[setFailure] 在演示过程中动态注入延迟与失败，
 * 用于验证发送中、失败与重试状态。
 */
class MockChatDataSource(
    private val localUserId: Long = DEFAULT_LOCAL_USER_ID,
    latencyMillis: Long = 0L,
    failure: ChatMockFailure? = null,
    private val now: () -> String = { isoNow() },
    initialNotifications: List<ChatNotificationDto>? = null
) : ChatDataSource {

    override val isMock: Boolean = true

    /** 模拟网络延迟（毫秒），可在演示/测试中动态调整。 */
    @Volatile
    var latencyMillis: Long = latencyMillis.coerceAtLeast(0L)
        private set

    /** 当前注入的失败；设为 null 可模拟网络恢复后重试成功。 */
    @Volatile
    var failure: ChatMockFailure? = failure
        private set

    fun setLatencyMillis(millis: Long) {
        latencyMillis = millis.coerceAtLeast(0L)
    }

    fun setFailure(failure: ChatMockFailure?) {
        this.failure = failure
    }

    /** 演示对方 ID 不得与当前登录用户冲突。 */
    private val demoPeerUserId: Long =
        if (localUserId == DEMO_PEER_USER_ID) DEMO_PEER_USER_ID + 1 else DEMO_PEER_USER_ID

    private val lock = Any()
    private val conversations = linkedMapOf<Long, MockConversation>()
    private var nextMessageId = DEMO_MESSAGE_COUNT + 1
    private val notifications = mutableListOf<ChatNotificationDto>().apply {
        addAll(initialNotifications ?: defaultNotifications())
    }

    init {
        seedDemoConversation()
    }

    override suspend fun listConversations(
        limit: Int,
        cursor: String?
    ): CursorPage<ChatConversationDto> {
        simulate(ChatMockOperation.CONVERSATIONS)
        val safeLimit = ChatDataSource.normalizedLimit(limit)
        synchronized(lock) {
            val all = conversations.values.map(::snapshotConversation)
            return pageDescending(
                all = all,
                limit = safeLimit,
                cursor = cursor,
                sortKey = { it.updatedAt.orEmpty() },
                id = { it.id }
            )
        }
    }

    override suspend fun openConversation(peerUserId: Long): ChatConversationDto {
        require(peerUserId > 0) { "peerUserId must be positive" }
        simulate(ChatMockOperation.OPEN_CONVERSATION)
        synchronized(lock) {
            conversations.values.firstOrNull { it.peer.id == peerUserId }?.let {
                return snapshotConversation(it)
            }
            // 与 peerUserId 绑定的稳定 ID，跨实例一致；演示对方仍使用演示会话 101。
            val conversationId = if (peerUserId == demoPeerUserId) {
                DEMO_CONVERSATION_ID
            } else {
                MOCK_CONVERSATION_BASE_ID + peerUserId
            }
            return snapshotConversation(ensureConversation(conversationId))
        }
    }

    override suspend fun listMessages(
        conversationId: Long,
        limit: Int,
        before: String?
    ): CursorPage<ChatMessageDto> {
        require(conversationId > 0) { "conversationId must be positive" }
        simulate(ChatMockOperation.MESSAGES)
        val safeLimit = ChatDataSource.normalizedLimit(limit)
        synchronized(lock) {
            val conversation = ensureConversation(conversationId)
            return pageAscending(
                all = conversation.messages,
                limit = safeLimit,
                cursor = before,
                sortKey = { it.createdAt.orEmpty() },
                id = { it.id }
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
        simulate(ChatMockOperation.SEND_MESSAGE)
        synchronized(lock) {
            // 先校验会话可访问，未知/越界 ID 即使携带历史 clientMessageId 也必须 404。
            val conversation = ensureConversation(conversationId)
            conversations.values.forEach { existing ->
                existing.messages.firstOrNull {
                    it.clientMessageId == safeClientMessageId
                }?.let { return it }
            }
            val message = ChatMessageDto(
                id = nextMessageId++,
                conversationId = conversationId,
                clientMessageId = safeClientMessageId,
                senderId = localUserId,
                receiverId = conversation.peer.id,
                type = ChatMessageDto.TYPE_TEXT,
                content = safeContent,
                createdAt = now()
            )
            conversation.messages.add(message)
            return message
        }
    }

    override suspend fun listNotifications(
        limit: Int,
        cursor: String?
    ): CursorPage<ChatNotificationDto> {
        simulate(ChatMockOperation.NOTIFICATIONS)
        val safeLimit = ChatDataSource.normalizedLimit(limit)
        synchronized(lock) {
            return pageDescending(
                all = notifications,
                limit = safeLimit,
                cursor = cursor,
                sortKey = { it.createdAt.orEmpty() },
                id = { it.id }
            )
        }
    }

    private suspend fun simulate(operation: ChatMockOperation) {
        if (latencyMillis > 0) {
            delay(latencyMillis)
        }
        failure?.takeIf { it.operation == operation }?.let { throw it.error }
    }

    private fun snapshotConversation(conversation: MockConversation): ChatConversationDto {
        val lastMessage = conversation.messages.maxByOrNull { it.createdAt.orEmpty() }
        return ChatConversationDto(
            id = conversation.id,
            peer = conversation.peer,
            lastMessage = lastMessage,
            updatedAt = lastMessage?.createdAt
        )
    }

    /**
     * Mock 内存态自愈：Room 中已存在的稳定会话 ID 在新建 Mock 实例里可能尚未创建。
     *
     * - 已存在直接返回；演示会话缺失时用种子恢复；
     * - 仅 `MOCK_CONVERSATION_BASE_ID..+MAX_MOCK_PEER_ID` 的稳定 ID 惰性创建空会话，
     *   peerUserId = id - BASE；
     * - 其余未知/越界 ID 仍按服务端行为返回 `CHAT_NOT_FOUND`，不把任意小 ID 当合法会话。
     *
     * 调用方必须持有 [lock]。
     */
    private fun ensureConversation(conversationId: Long): MockConversation {
        conversations[conversationId]?.let { return it }
        if (conversationId == DEMO_CONVERSATION_ID) {
            seedDemoConversation()
            return conversations[conversationId] ?: throw chatNotFound()
        }
        val peerUserId = conversationId - MOCK_CONVERSATION_BASE_ID
        if (peerUserId <= 0L || peerUserId > MAX_MOCK_PEER_ID) {
            throw chatNotFound()
        }
        val conversation = MockConversation(
            id = conversationId,
            peer = UserDto(
                id = peerUserId,
                username = "user_$peerUserId",
                nickname = "好友$peerUserId"
            )
        )
        conversations[conversationId] = conversation
        return conversation
    }

    /**
     * 降序分页：游标编码最后一页最后一项的排序键与 id。
     * 下一页只取严格排在游标之后的项，保证按 updatedAt/createdAt 排序时
     * 不依赖 id 顺序，也不会重复或遗漏。
     */
    private fun <T> pageDescending(
        all: List<T>,
        limit: Int,
        cursor: String?,
        sortKey: (T) -> String,
        id: (T) -> Long
    ): CursorPage<T> {
        val sorted = all.sortedWith(
            compareByDescending<T> { sortKey(it) }.thenByDescending { id(it) }
        )
        val decoded = decodeCursor(cursor)
        val candidates = if (decoded == null) {
            sorted
        } else {
            sorted.filter {
                val key = sortKey(it)
                key < decoded.sortKey || (key == decoded.sortKey && id(it) < decoded.id)
            }
        }
        val page = candidates.take(limit)
        val last = page.lastOrNull()
        val hasMore = candidates.size > page.size
        return CursorPage(
            items = page,
            nextCursor = if (hasMore && last != null) {
                encodeCursor(sortKey(last), id(last))
            } else {
                null
            }
        )
    }

    /**
     * 升序分页：首屏取最新一页（升序返回），游标指向本页最早一项；
     * 下一页取严格早于游标的项，避免 id 与 createdAt 顺序不一致导致重复/遗漏。
     */
    private fun <T> pageAscending(
        all: List<T>,
        limit: Int,
        cursor: String?,
        sortKey: (T) -> String,
        id: (T) -> Long
    ): CursorPage<T> {
        val sorted = all.sortedWith(
            compareBy<T> { sortKey(it) }.thenBy { id(it) }
        )
        val decoded = decodeCursor(cursor)
        val candidates = if (decoded == null) {
            sorted
        } else {
            sorted.filter {
                val key = sortKey(it)
                key < decoded.sortKey || (key == decoded.sortKey && id(it) < decoded.id)
            }
        }
        val page = candidates.takeLast(limit)
        val oldest = page.firstOrNull()
        val hasMore = candidates.size > page.size
        return CursorPage(
            items = page,
            nextCursor = if (hasMore && oldest != null) {
                encodeCursor(sortKey(oldest), id(oldest))
            } else {
                null
            }
        )
    }

    private fun encodeCursor(sortKey: String, id: Long): String =
        "$sortKey$CURSOR_SEPARATOR$id"

    private fun decodeCursor(cursor: String?): MockPageCursor? {
        val raw = ChatDataSource.normalizedCursor(cursor) ?: return null
        val separator = raw.lastIndexOf(CURSOR_SEPARATOR)
        if (separator < 0) return null
        val id = raw.substring(separator + 1).toLongOrNull() ?: return null
        return MockPageCursor(
            sortKey = raw.substring(0, separator),
            id = id
        )
    }

    private fun defaultNotifications(): List<ChatNotificationDto> = listOf(
        ChatNotificationDto(
            id = 1L,
            type = ChatNotificationDto.TYPE_RESERVATION,
            title = "新的预订请求",
            body = "好友想预订你的菜谱「红烧肉」，请在订单中查看详情。",
            orderId = null,
            createdAt = "2026-10-20T09:10:00Z"
        )
    )

    private fun seedDemoConversation() {
        val peer = UserDto(
            id = demoPeerUserId,
            username = "lin_xiaochu",
            nickname = "林小厨"
        )
        val messages = mutableListOf(
            ChatMessageDto(
                id = 1L,
                conversationId = DEMO_CONVERSATION_ID,
                clientMessageId = "demo-1",
                senderId = peer.id,
                receiverId = localUserId,
                content = "你好，看到你做的红烧肉了，看起来很棒！",
                createdAt = "2026-10-20T09:00:00Z"
            ),
            ChatMessageDto(
                id = 2L,
                conversationId = DEMO_CONVERSATION_ID,
                clientMessageId = "demo-2",
                senderId = localUserId,
                receiverId = peer.id,
                content = "谢谢！周末有空可以来尝尝。",
                createdAt = "2026-10-20T09:01:00Z"
            ),
            ChatMessageDto(
                id = 3L,
                conversationId = DEMO_CONVERSATION_ID,
                clientMessageId = "demo-3",
                senderId = peer.id,
                receiverId = localUserId,
                content = "好呀，那就周六见。",
                createdAt = "2026-10-20T09:02:00Z"
            )
        )
        conversations[DEMO_CONVERSATION_ID] = MockConversation(
            id = DEMO_CONVERSATION_ID,
            peer = peer,
            messages = messages
        )
        nextMessageId = DEMO_MESSAGE_COUNT + 1
    }

    private fun chatNotFound(): AuthException =
        AuthException(
            code = "CHAT_NOT_FOUND",
            message = "会话不存在或已被删除",
            httpStatus = 404
        )

    private data class MockConversation(
        val id: Long,
        val peer: UserDto,
        val messages: MutableList<ChatMessageDto> = mutableListOf()
    )

    private data class MockPageCursor(
        val sortKey: String,
        val id: Long
    )

    companion object {
        const val DEFAULT_LOCAL_USER_ID = 1L
        const val DEMO_PEER_USER_ID = 2L
        const val DEMO_CONVERSATION_ID = 101L
        const val DEMO_MESSAGE_COUNT = 3L

        /** 非演示会话 ID = 基数 + peerUserId，保证跨 Mock 实例稳定且不冲突。 */
        const val MOCK_CONVERSATION_BASE_ID = 1_000L

        /** 稳定会话可接受的 peerUserId 上限；越界 ID 必须返回 CHAT_NOT_FOUND。 */
        const val MAX_MOCK_PEER_ID = 1_000_000L

        private const val CURSOR_SEPARATOR = "|"
    }
}

private fun isoNow(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date())
}
