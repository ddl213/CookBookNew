package com.rhys.chat.data

import com.example.network.bean.ChatConversationDto
import com.example.network.bean.ChatMessageDto
import com.example.network.bean.ChatNotificationDto
import com.example.network.bean.CursorPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 数据源配置解析与切换，不访问网络。 */
class ChatDataSourceConfigTest {

    @Test
    fun `mock value resolves to mock source`() {
        val resolution = ChatDataSourceConfig.resolve("mock")
        assertEquals(ChatDataSourceKind.MOCK, resolution.kind)
        assertFalse(resolution.fallback)
    }

    @Test
    fun `remote value is case insensitive and trimmed`() {
        val resolution = ChatDataSourceConfig.resolve("  REMOTE  ")
        assertEquals(ChatDataSourceKind.REMOTE, resolution.kind)
        assertFalse(resolution.fallback)
    }

    @Test
    fun `invalid value falls back to remote and marks fallback`() {
        val resolution = ChatDataSourceConfig.resolve("socket")
        assertEquals(ChatDataSourceKind.REMOTE, resolution.kind)
        assertTrue(resolution.fallback)
        assertEquals("socket", resolution.rawValue)
    }

    @Test
    fun `blank value also falls back to remote`() {
        assertTrue(ChatDataSourceConfig.resolve("   ").fallback)
        assertTrue(ChatDataSourceConfig.resolve(null).fallback)
    }

    @Test
    fun `create selects mock factory without warning`() {
        val fakeMock = FakeDataSource(isMock = true)
        val created = ChatDataSourceConfig.create(
            raw = "mock",
            onWarning = { fail("unexpected warning: $it") },
            mockFactory = { fakeMock }
        )
        assertSame(fakeMock, created)
    }

    @Test
    fun `create falls back to remote factory and reports warning`() {
        val warnings = mutableListOf<String>()
        val fakeRemote = FakeDataSource(isMock = false)
        val created = ChatDataSourceConfig.create(
            raw = "bogus",
            onWarning = { warnings.add(it) },
            remoteFactory = { fakeRemote }
        )
        assertSame(fakeRemote, created)
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("bogus"))
        assertTrue(warnings[0].contains("remote"))
    }

    private class FakeDataSource(override val isMock: Boolean) : ChatDataSource {
        override suspend fun listConversations(
            limit: Int,
            cursor: String?
        ): CursorPage<ChatConversationDto> = CursorPage()

        override suspend fun openConversation(peerUserId: Long): ChatConversationDto =
            ChatConversationDto()

        override suspend fun listMessages(
            conversationId: Long,
            limit: Int,
            before: String?
        ): CursorPage<ChatMessageDto> = CursorPage()

        override suspend fun sendMessage(
            conversationId: Long,
            clientMessageId: String,
            content: String
        ): ChatMessageDto = ChatMessageDto()

        override suspend fun listNotifications(
            limit: Int,
            cursor: String?
        ): CursorPage<ChatNotificationDto> = CursorPage()
    }
}
