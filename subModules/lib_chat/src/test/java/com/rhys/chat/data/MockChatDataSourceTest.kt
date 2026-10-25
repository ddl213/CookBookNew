package com.rhys.chat.data

import com.example.network.AuthException
import com.example.network.bean.ChatNotificationDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** 内存 Mock 数据源：演示数据、幂等、分页与失败注入。 */
class MockChatDataSourceTest {

    @Test
    fun `demo conversation and messages are available`() = runBlocking {
        val dataSource = MockChatDataSource()

        assertTrue(dataSource.isMock)
        val conversations = dataSource.listConversations()
        assertEquals(1, conversations.items.size)
        assertEquals(MockChatDataSource.DEMO_CONVERSATION_ID, conversations.items[0].id)
        assertEquals("林小厨", conversations.items[0].peer?.nickname)

        val messages = dataSource.listMessages(MockChatDataSource.DEMO_CONVERSATION_ID)
        assertEquals(3, messages.items.size)
        assertEquals(listOf(1L, 2L, 3L), messages.items.map { it.id })
        assertNull(messages.nextCursor)
    }

    @Test
    fun `open conversation is idempotent for the same peer`() = runBlocking {
        val dataSource = MockChatDataSource()

        val first = dataSource.openConversation(MockChatDataSource.DEMO_PEER_USER_ID)
        val second = dataSource.openConversation(MockChatDataSource.DEMO_PEER_USER_ID)

        assertEquals(MockChatDataSource.DEMO_CONVERSATION_ID, first.id)
        assertEquals(first.id, second.id)
    }

    @Test
    fun `open conversation uses stable per-peer ids across mock instances`() = runBlocking {
        val first = MockChatDataSource()
        val second = MockChatDataSource()

        val peer3First = first.openConversation(3L)
        val peer3Second = second.openConversation(3L)

        assertEquals(MockChatDataSource.MOCK_CONVERSATION_BASE_ID + 3L, peer3First.id)
        assertEquals(peer3First.id, peer3Second.id)

        val peer4 = second.openConversation(4L)
        assertEquals(MockChatDataSource.MOCK_CONVERSATION_BASE_ID + 4L, peer4.id)
        assertNotEquals(peer3First.id, peer4.id)

        assertEquals(
            MockChatDataSource.DEMO_CONVERSATION_ID,
            first.openConversation(MockChatDataSource.DEMO_PEER_USER_ID).id
        )

        val sent = first.sendMessage(peer3First.id, "stable-1", "跨实例会话")
        assertEquals(peer3First.id, sent.conversationId)
        val history = first.listMessages(peer3First.id)
        assertEquals(1, history.items.size)
        assertEquals("跨实例会话", history.items[0].content)
    }

    @Test
    fun `stable conversation id is lazily restored in a new mock instance`() = runBlocking {
        val first = MockChatDataSource()
        val conversation = first.openConversation(3L)
        assertEquals(MockChatDataSource.MOCK_CONVERSATION_BASE_ID + 3L, conversation.id)
        first.sendMessage(conversation.id, "instance-a-1", "实例 A 消息")

        // 新 Mock 实例只种了演示会话；进入 Room 中已有的 1003 时必须自愈为空会话，
        // 不能把正常重入当成 404，也不能复制另一个实例的内存消息。
        val second = MockChatDataSource()
        val freshHistory = second.listMessages(conversation.id)
        assertTrue(freshHistory.items.isEmpty())

        val sent = second.sendMessage(conversation.id, "instance-b-1", "实例 B 消息")
        assertEquals(conversation.id, sent.conversationId)
        assertEquals(3L, sent.receiverId)

        val secondHistory = second.listMessages(conversation.id)
        assertEquals(listOf("实例 B 消息"), secondHistory.items.map { it.content })
        assertEquals(conversation.id, secondHistory.items[0].conversationId)

        val conversations = second.listConversations(limit = 50).items
        assertEquals(1, conversations.count { it.id == conversation.id })
    }

    @Test
    fun `unknown or out-of-range conversation ids remain not found`() = runBlocking {
        val dataSource = MockChatDataSource()
        val invalidIds = listOf(
            500L,
            MockChatDataSource.MOCK_CONVERSATION_BASE_ID,
            MockChatDataSource.MOCK_CONVERSATION_BASE_ID + MockChatDataSource.MAX_MOCK_PEER_ID + 1L
        )

        for (invalidId in invalidIds) {
            try {
                dataSource.listMessages(invalidId)
                fail("expected CHAT_NOT_FOUND for listMessages($invalidId)")
            } catch (e: AuthException) {
                assertEquals("CHAT_NOT_FOUND", e.code)
                assertEquals(404, e.httpStatus)
            }
        }

        try {
            dataSource.sendMessage(500L, "unknown-conversation", "不应写入")
            fail("expected CHAT_NOT_FOUND for sendMessage(500)")
        } catch (e: AuthException) {
            assertEquals("CHAT_NOT_FOUND", e.code)
            assertEquals(404, e.httpStatus)
        }

        // 即使 clientMessageId 已存在于其他会话，未知会话 ID 也不能借幂等分支绕过 404。
        dataSource.sendMessage(MockChatDataSource.DEMO_CONVERSATION_ID, "shared-client", "已有消息")
        try {
            dataSource.sendMessage(500L, "shared-client", "不应返回旧消息")
            fail("expected CHAT_NOT_FOUND for unknown conversation with existing client id")
        } catch (e: AuthException) {
            assertEquals("CHAT_NOT_FOUND", e.code)
            assertEquals(404, e.httpStatus)
        }
    }

    @Test
    fun `shifted demo peer still maps to demo conversation`() = runBlocking {
        val dataSource = MockChatDataSource(localUserId = MockChatDataSource.DEMO_PEER_USER_ID)
        val demoPeer = MockChatDataSource.DEMO_PEER_USER_ID + 1

        assertEquals(
            MockChatDataSource.DEMO_CONVERSATION_ID,
            dataSource.openConversation(demoPeer).id
        )
    }

    @Test
    fun `send message is idempotent by client message id`() = runBlocking {
        val dataSource = MockChatDataSource()

        val first = dataSource.sendMessage(
            MockChatDataSource.DEMO_CONVERSATION_ID,
            "client-retry-1",
            "第一条"
        )
        val retry = dataSource.sendMessage(
            MockChatDataSource.DEMO_CONVERSATION_ID,
            "client-retry-1",
            "第一条"
        )

        assertEquals(first.id, retry.id)
        val messages = dataSource.listMessages(MockChatDataSource.DEMO_CONVERSATION_ID, limit = 50)
        assertEquals(4, messages.items.size)
        assertEquals(1, messages.items.count { it.clientMessageId == "client-retry-1" })
    }

    @Test
    fun `different client ids append separate messages`() = runBlocking {
        val dataSource = MockChatDataSource()

        dataSource.sendMessage(MockChatDataSource.DEMO_CONVERSATION_ID, "client-1", "一")
        dataSource.sendMessage(MockChatDataSource.DEMO_CONVERSATION_ID, "client-2", "二")

        val messages = dataSource.listMessages(MockChatDataSource.DEMO_CONVERSATION_ID, limit = 50)
        assertEquals(5, messages.items.size)
    }

    @Test
    fun `history pagination returns older page cursor`() = runBlocking {
        val dataSource = MockChatDataSource()

        val latest = dataSource.listMessages(
            MockChatDataSource.DEMO_CONVERSATION_ID,
            limit = 2
        )
        assertEquals(listOf(2L, 3L), latest.items.map { it.id })
        assertNotNull(latest.nextCursor)

        val older = dataSource.listMessages(
            MockChatDataSource.DEMO_CONVERSATION_ID,
            limit = 2,
            before = latest.nextCursor
        )
        assertEquals(listOf(1L), older.items.map { it.id })
        assertNull(older.nextCursor)
    }

    @Test
    fun `send failure can be injected`() = runBlocking {
        val dataSource = MockChatDataSource(
            failure = ChatMockFailure(ChatMockOperation.SEND_MESSAGE)
        )

        try {
            dataSource.sendMessage(MockChatDataSource.DEMO_CONVERSATION_ID, "client-1", "失败")
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("模拟网络失败", e.message)
        }

        // 失败不应写入任何消息。
        val messages = dataSource.listMessages(MockChatDataSource.DEMO_CONVERSATION_ID, limit = 50)
        assertEquals(3, messages.items.size)
    }

    @Test
    fun `content validation rejects blank and oversized content`() = runBlocking {
        val dataSource = MockChatDataSource()

        try {
            dataSource.sendMessage(MockChatDataSource.DEMO_CONVERSATION_ID, "client-1", "   ")
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }

        try {
            dataSource.sendMessage(
                MockChatDataSource.DEMO_CONVERSATION_ID,
                "client-2",
                "字".repeat(ChatDataSource.MAX_MESSAGE_LENGTH + 1)
            )
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }

        val messages = dataSource.listMessages(MockChatDataSource.DEMO_CONVERSATION_ID, limit = 50)
        assertEquals(3, messages.items.size)
    }

    @Test
    fun `notifications include reservation item`() = runBlocking {
        val page = MockChatDataSource().listNotifications()

        assertFalse(page.items.isEmpty())
        assertEquals("RESERVATION", page.items[0].type)
        assertNotNull(page.items[0].title)
    }

    @Test
    fun `conversation pages do not duplicate or omit when updatedAt order differs from id`() =
        runBlocking {
            val timestamps = ArrayDeque(
                listOf("2026-10-25T11:00:00Z", "2026-10-25T10:00:00Z")
            )
            val dataSource = MockChatDataSource(now = { timestamps.removeFirst() })

            val newerIdConversation = dataSource.openConversation(3)
            val olderIdConversation = dataSource.openConversation(4)
            assertEquals(MockChatDataSource.MOCK_CONVERSATION_BASE_ID + 3L, newerIdConversation.id)
            assertEquals(MockChatDataSource.MOCK_CONVERSATION_BASE_ID + 4L, olderIdConversation.id)

            // 102 的更新时间晚于 103，用于制造 updatedAt 顺序与 id 顺序不一致。
            dataSource.sendMessage(newerIdConversation.id, "conv-102", "较晚更新的会话")
            dataSource.sendMessage(olderIdConversation.id, "conv-103", "较早更新的会话")

            val seen = mutableListOf<Long>()
            var cursor: String? = null
            var pages = 0
            do {
                val page = dataSource.listConversations(limit = 2, cursor = cursor)
                assertTrue(page.items.size <= 2)
                seen += page.items.map { it.id }
                cursor = page.nextCursor
                pages++
            } while (cursor != null)

            // updatedAt 倒序应为 1003 -> 1004 -> 101；游标必须按更新时间而非 id 推进。
            assertEquals(
                listOf(
                    MockChatDataSource.MOCK_CONVERSATION_BASE_ID + 3L,
                    MockChatDataSource.MOCK_CONVERSATION_BASE_ID + 4L,
                    MockChatDataSource.DEMO_CONVERSATION_ID
                ),
                seen
            )
            assertEquals(3, seen.size)
            assertEquals(3, seen.toSet().size)
            assertEquals(2, pages)
        }

    @Test
    fun `notification pages do not duplicate or omit when createdAt order differs from id`() =
        runBlocking {
            val dataSource = MockChatDataSource(
                initialNotifications = listOf(
                    notification(1L, "2026-10-20T10:00:00Z"),
                    notification(2L, "2026-10-20T12:00:00Z"),
                    notification(3L, "2026-10-20T11:00:00Z"),
                    notification(4L, "2026-10-20T09:00:00Z"),
                    notification(5L, "2026-10-20T08:00:00Z")
                )
            )

            val seen = mutableListOf<Long>()
            var cursor: String? = null
            do {
                val page = dataSource.listNotifications(limit = 2, cursor = cursor)
                assertTrue(page.items.size <= 2)
                seen += page.items.map { it.id }
                cursor = page.nextCursor
            } while (cursor != null)

            assertEquals(listOf(2L, 3L, 1L, 4L, 5L), seen)
            assertEquals(5, seen.size)
            assertEquals(5, seen.toSet().size)
        }

    @Test
    fun `mock uses authenticated owner as sender and a non-conflicting peer`() = runBlocking {
        val dataSource = MockChatDataSource(localUserId = 5L)
        val messages = dataSource.listMessages(
            MockChatDataSource.DEMO_CONVERSATION_ID,
            limit = 50
        ).items

        assertEquals(5L, messages.single { it.clientMessageId == "demo-2" }.senderId)
        assertTrue(
            messages.filter { it.clientMessageId != "demo-2" }
                .all { it.senderId != 5L }
        )

        val sent = dataSource.sendMessage(
            MockChatDataSource.DEMO_CONVERSATION_ID,
            "owner-5",
            "自己发送"
        )
        assertEquals(5L, sent.senderId)
    }

    @Test
    fun `demo peer id shifts when owner already uses the default peer id`() = runBlocking {
        val ownerId = MockChatDataSource.DEMO_PEER_USER_ID
        val dataSource = MockChatDataSource(localUserId = ownerId)
        val messages = dataSource.listMessages(
            MockChatDataSource.DEMO_CONVERSATION_ID,
            limit = 50
        ).items

        assertTrue(messages.all { it.senderId != it.receiverId })
        assertEquals(ownerId, messages.single { it.clientMessageId == "demo-2" }.senderId)
        assertTrue(
            messages.filter { it.clientMessageId != "demo-2" }
                .all { it.senderId != ownerId }
        )
    }

    private fun notification(id: Long, createdAt: String) = ChatNotificationDto(
        id = id,
        type = ChatNotificationDto.TYPE_RESERVATION,
        title = "预订通知 $id",
        body = "正文 $id",
        orderId = null,
        createdAt = createdAt
    )
}
