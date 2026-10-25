package com.rhys.chat.data

import com.example.network.AuthException
import com.rhys.chat.data.crypto.ChatCipher
import com.rhys.chat.data.crypto.ChatDecryption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repository 的演示标识、默认实时源与未登录边界。
 *
 * 这些路径不访问数据库，因此无需 Robolectric；加密/落盘行为见
 * [ChatRepositoryStorageTest]。
 */
class ChatRepositoryTest {

    @Test
    fun `isMock delegates to data source`() {
        assertTrue(repository(isMock = true).isMock)
        assertEquals(false, repository(isMock = false).isMock)
    }

    @Test
    fun `default realtime source reports unavailable`() = runBlocking {
        val repository = repository()
        assertEquals(
            ChatRealtimeState.UNAVAILABLE,
            repository.realtime.connectionState.first()
        )
        assertNull(repository.realtime.events.firstOrNull())
    }

    @Test
    fun `logged out repository exposes empty observations`() = runBlocking {
        val repository = repository(ownerId = null)

        assertTrue(repository.observeConversations().first().isEmpty())
        assertTrue(repository.observeMessages(TEST_CONVERSATION_ID).first().isEmpty())
        assertTrue(repository.observeNotifications().first().isEmpty())
    }

    @Test
    fun `logged out write fails with unauthorized before touching storage`() {
        var databaseRequested = false

        val guarded = ChatRepository(
            dataSource = FakeChatDataSource(),
            databaseProvider = {
                databaseRequested = true
                throw AssertionError("database must not be accessed while logged out")
            },
            cipher = object : ChatCipher {
                override fun encrypt(plaintext: String): String =
                    throw AssertionError("cipher must not be used while logged out")

                override fun decrypt(encoded: String): ChatDecryption =
                    ChatDecryption.Unavailable
            },
            ownerProvider = ChatOwnerProvider { null }
        )

        val error = runCatching {
            runBlocking { guarded.sendMessage(TEST_CONVERSATION_ID, "未登录") }
        }.exceptionOrNull()

        assertTrue(error is AuthException)
        assertEquals("UNAUTHORIZED", (error as AuthException).code)
        assertEquals(false, databaseRequested)
    }

    private fun repository(
        isMock: Boolean = true,
        ownerId: Long? = 1L
    ): ChatRepository = ChatRepository(
        dataSource = FakeChatDataSource(isMock = isMock),
        databaseProvider = { throw AssertionError("database not expected") },
        cipher = object : ChatCipher {
            override fun encrypt(plaintext: String): String = plaintext

            override fun decrypt(encoded: String): ChatDecryption =
                ChatDecryption.Success(encoded)
        },
        ownerProvider = ChatOwnerProvider { ownerId }
    )
}
