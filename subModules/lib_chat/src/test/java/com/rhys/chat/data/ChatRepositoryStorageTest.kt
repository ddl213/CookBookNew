package com.rhys.chat.data

import com.example.network.AuthException
import com.example.network.bean.ChatConversationDto
import com.example.network.bean.ChatMessageDto
import com.example.network.bean.UserDto
import com.rhys.chat.data.crypto.ChatCipher
import com.rhys.chat.data.crypto.ChatCipherException
import com.rhys.chat.data.crypto.ChatDecryption
import com.rhys.chat.data.local.ChatDatabase
import com.rhys.chat.data.local.ChatMessageEntity
import com.rhys.chat.domain.ChatMessageStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Repository + 加密 Room 缓存的行为验证（in-memory，不访问网络）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatRepositoryStorageTest {

    private lateinit var db: ChatDatabase
    private val cipher = newTestCipher()

    @Volatile
    private var currentOwner: Long? = 1L

    @Before
    fun setUp() {
        db = ChatDatabase.buildInMemory(RuntimeEnvironment.getApplication())
        currentOwner = 1L
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `user visible text is stored encrypted and decrypts back`() {
        val repository = repository(FakeChatDataSource())
        runBlocking {
            repository.refreshConversations()
            repository.refreshMessages(TEST_CONVERSATION_ID)
            repository.refreshNotifications()
        }

        val conversation = flowValue(db.chatDao().observeConversations(1L)).single()
        assertEncrypted(conversation.peerNicknameCipher, TEST_PEER_NICKNAME)
        assertEncrypted(conversation.peerAvatarCipher, TEST_PEER_AVATAR)
        assertEncrypted(conversation.lastMessagePreviewCipher, TEST_MESSAGE_TWO)

        val messages = flowValue(db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID))
        assertEquals(2, messages.size)
        assertEncrypted(messages[0].contentCipher, TEST_MESSAGE_ONE)
        assertEncrypted(messages[1].contentCipher, TEST_MESSAGE_TWO)

        val notification = flowValue(db.chatDao().observeNotifications(1L)).single()
        assertEncrypted(notification.titleCipher, TEST_NOTIFICATION_TITLE)
        assertEncrypted(notification.bodyCipher, TEST_NOTIFICATION_BODY)
    }

    @Test
    fun `owners cannot observe each other's cached data`() {
        val repository = repository(FakeChatDataSource())
        runBlocking {
            repository.refreshConversations()
            repository.refreshMessages(TEST_CONVERSATION_ID)
            repository.refreshNotifications()
        }

        currentOwner = 2L
        assertTrue(flowValue(repository.observeConversations()).isEmpty())
        assertTrue(flowValue(repository.observeMessages(TEST_CONVERSATION_ID)).isEmpty())
        assertTrue(flowValue(repository.observeNotifications()).isEmpty())
        assertEquals(0, runBlocking { db.chatDao().countMessages(2L) })

        runBlocking {
            repository.refreshConversations()
            repository.refreshMessages(TEST_CONVERSATION_ID)
        }
        assertEquals(2, runBlocking { db.chatDao().countMessages(1L) })
        assertEquals(2, runBlocking { db.chatDao().countMessages(2L) })

        currentOwner = 1L
        assertEquals(2, flowValue(repository.observeMessages(TEST_CONVERSATION_ID)).size)
    }

    @Test
    fun `logged out user sees empty cache and cannot write`() {
        val repository = repository(FakeChatDataSource())
        currentOwner = null

        assertTrue(flowValue(repository.observeConversations()).isEmpty())
        assertTrue(flowValue(repository.observeMessages(TEST_CONVERSATION_ID)).isEmpty())
        assertTrue(flowValue(repository.observeNotifications()).isEmpty())

        val error = runCatching {
            runBlocking { repository.sendMessage(TEST_CONVERSATION_ID, "未登录") }
        }.exceptionOrNull()
        assertTrue(error is AuthException)
        assertEquals("UNAUTHORIZED", (error as AuthException).code)
        assertEquals(0, runBlocking { db.chatDao().countMessages(1L) })
    }

    @Test
    fun `failed send is marked FAILED and retry reuses client id and content`() {
        val dataSource = FakeChatDataSource().apply { failSend = true }
        val repository = repository(dataSource)

        val error = runCatching {
            runBlocking { repository.sendMessage(TEST_CONVERSATION_ID, "周六见") }
        }.exceptionOrNull()
        assertTrue(error is AuthException)

        val failed = flowValue(
            db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID)
        ).single()
        assertEquals(ChatMessageStatus.FAILED.name, failed.localStatus)
        assertEquals("周六见", decrypt(failed.contentCipher))

        dataSource.failSend = false
        val retried = runBlocking { repository.retryMessage(failed.clientMessageId) }

        assertEquals(ChatMessageStatus.SENT, retried.status)
        assertEquals(failed.clientMessageId, retried.clientMessageId)
        assertEquals("周六见", retried.content)
        assertNotNull(retried.serverId)
        val stored = flowValue(db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID))
        assertEquals(1, stored.size)
        assertEquals(ChatMessageStatus.SENT.name, stored[0].localStatus)
        assertEquals(1, dataSource.sentClientMessageIds.distinct().size)
        assertEquals(listOf("周六见", "周六见"), dataSource.sentContents)
    }

    @Test
    fun `remote echo keeps local failed status and never duplicates rows`() {
        val dataSource = FakeChatDataSource().apply { failSend = true }
        val repository = repository(dataSource)
        runCatching {
            runBlocking { repository.sendMessage(TEST_CONVERSATION_ID, "网络抖动") }
        }
        val failed = flowValue(
            db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID)
        ).single()

        val echo = ChatMessageDto(
            id = 777L,
            conversationId = TEST_CONVERSATION_ID,
            clientMessageId = failed.clientMessageId,
            senderId = 1L,
            receiverId = TEST_PEER_ID,
            type = ChatMessageDto.TYPE_TEXT,
            content = "网络抖动",
            createdAt = "2026-10-25T12:00:00Z"
        )
        dataSource.messages += echo
        dataSource.messages += echo

        runBlocking { repository.refreshMessages(TEST_CONVERSATION_ID) }

        val rows = flowValue(db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID))
        val afterEcho = rows.first { it.clientMessageId == failed.clientMessageId }
        // 两条远端历史 + 一条本地失败记录；重复 echo 不新增行。
        assertEquals(3, rows.size)
        assertEquals(1, rows.count { it.clientMessageId == failed.clientMessageId })
        assertEquals(ChatMessageStatus.FAILED.name, afterEcho.localStatus)
        assertEquals(777L, afterEcho.serverId)

        dataSource.failSend = false
        val sent = runBlocking { repository.retryMessage(failed.clientMessageId) }
        assertEquals(ChatMessageStatus.SENT, sent.status)
        val afterRetry = flowValue(db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID))
        assertEquals(3, afterRetry.size)
        assertEquals(1, afterRetry.count { it.clientMessageId == failed.clientMessageId })
    }

    @Test
    fun `mock send keeps authenticated owner as sender`() {
        currentOwner = 5L
        val mock = MockChatDataSource(localUserId = 5L)
        val repository = repository(mock)

        val sent = runBlocking {
            repository.sendMessage(MockChatDataSource.DEMO_CONVERSATION_ID, "owner-5")
        }

        assertEquals(5L, sent.senderId)
        val row = flowValue(
            db.chatDao().observeMessages(5L, MockChatDataSource.DEMO_CONVERSATION_ID)
        ).single()
        assertEquals(5L, row.senderId)
        assertEquals(ChatMessageStatus.SENT.name, row.localStatus)
    }

    @Test
    fun `send success ignores mismatched data source sender`() {
        currentOwner = 5L
        // Fake 数据源固定返回 senderId=1，模拟服务端/Mock 身份不一致。
        val repository = repository(FakeChatDataSource())

        val sent = runBlocking { repository.sendMessage(TEST_CONVERSATION_ID, "身份保持") }

        assertEquals(5L, sent.senderId)
        val row = flowValue(
            db.chatDao().observeMessages(5L, TEST_CONVERSATION_ID)
        ).single { it.clientMessageId == sent.clientMessageId }
        assertEquals(5L, row.senderId)
    }

    @Test
    fun `owner switch during refresh rejects stale writes`() = runBlocking {
        currentOwner = 1L
        val dataSource = FakeChatDataSource()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        dataSource.messagesStarted = started
        dataSource.messagesGate = gate
        val repository = repository(dataSource)

        val job = async(Dispatchers.IO) {
            runCatching { repository.refreshMessages(TEST_CONVERSATION_ID) }
        }
        started.await()
        currentOwner = 2L
        gate.complete(Unit)
        val error = job.await().exceptionOrNull()

        assertTrue(error is AuthException)
        val authError = error as AuthException
        assertEquals("UNAUTHORIZED", authError.code)
        assertEquals(401, authError.httpStatus)
        assertEquals(0, db.chatDao().countMessages(1L))
        assertEquals(0, db.chatDao().countMessages(2L))
    }

    @Test
    fun `owner switch during send does not update stale local row`() = runBlocking {
        currentOwner = 1L
        val dataSource = FakeChatDataSource()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        dataSource.sendStarted = started
        dataSource.sendGate = gate
        val repository = repository(dataSource)

        val job = async(Dispatchers.IO) {
            runCatching { repository.sendMessage(TEST_CONVERSATION_ID, "并发切换") }
        }
        started.await()
        currentOwner = 2L
        gate.complete(Unit)
        val error = job.await().exceptionOrNull()

        assertTrue(error is AuthException)
        val authError = error as AuthException
        assertEquals("UNAUTHORIZED", authError.code)
        assertEquals(401, authError.httpStatus)
        // 切换前写入的 SENDING 行不得被切换后的结果覆盖为 SENT。
        val ownerOneRows = flowValue(
            db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID)
        )
        assertEquals(1, ownerOneRows.size)
        assertEquals(ChatMessageStatus.SENDING.name, ownerOneRows[0].localStatus)
        assertNull(ownerOneRows[0].serverId)
        assertEquals(0, db.chatDao().countMessages(2L))
    }

    @Test
    fun `retry rechecks owner before reading local row`() {
        val repository = ChatRepository(
            dataSource = FakeChatDataSource(),
            databaseProvider = { db },
            cipher = cipher,
            ownerProvider = SequencedOwnerProvider(1L, 2L)
        )

        // requireOwner 读到 1；进入读取块后 owner 已变为 2，必须在 findMessage 前失败。
        val error = runCatching {
            runBlocking { repository.retryMessage("missing") }
        }.exceptionOrNull()

        assertTrue(error is AuthException)
        val authError = error as AuthException
        assertEquals("UNAUTHORIZED", authError.code)
        assertEquals(401, authError.httpStatus)
        assertEquals(0, runBlocking { db.chatDao().countMessages(1L) })
    }

    @Test
    fun `retry rechecks owner before returning already sent message`() {
        val sentRow = ChatMessageEntity(
            ownerUserId = 1L,
            clientMessageId = "sent-1",
            serverId = 10L,
            conversationId = TEST_CONVERSATION_ID,
            senderId = 1L,
            receiverId = TEST_PEER_ID,
            type = ChatMessageDto.TYPE_TEXT,
            contentCipher = cipher.encrypt("已发送"),
            createdAtMillis = 1_000L,
            createdAtRaw = "2026-10-20T09:00:00Z",
            localStatus = ChatMessageStatus.SENT.name
        )
        runBlocking { db.chatDao().upsertMessage(sentRow) }

        val repository = ChatRepository(
            dataSource = FakeChatDataSource(),
            databaseProvider = { db },
            cipher = cipher,
            ownerProvider = SequencedOwnerProvider(1L, 1L, 2L)
        )

        // requireOwner=1、读取前复核=1、提前返回前复核=2 → 不得返回旧 owner 的 SENT 消息。
        val error = runCatching {
            runBlocking { repository.retryMessage("sent-1") }
        }.exceptionOrNull()

        assertTrue(error is AuthException)
        val authError = error as AuthException
        assertEquals("UNAUTHORIZED", authError.code)
        assertEquals(401, authError.httpStatus)
        val stored = flowValue(
            db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID)
        ).single()
        assertEquals(ChatMessageStatus.SENT.name, stored.localStatus)
    }

    @Test
    fun `conversation refresh never regresses a newer local preview`() {
        val dataSource = FakeChatDataSource()
        val repository = repository(dataSource)
        runBlocking { repository.refreshConversations() }

        val newerRaw = "2027-01-15T08:00:00Z"
        val newerMillis = ChatTime.parseMillis(newerRaw)
        val newerPreview = "较新的本地预览"
        val newerCipher = cipher.encrypt(newerPreview)
        runBlocking {
            db.chatDao().updateConversationPreview(
                ownerUserId = 1L,
                conversationId = TEST_CONVERSATION_ID,
                previewCipher = newerCipher,
                atMillis = newerMillis,
                atRaw = newerRaw
            )
        }

        // 持续用较旧的 DTO 刷新：预览、最后消息时间与 updatedAt 都不得回退。
        repeat(2) {
            val domain = runBlocking { repository.refreshConversations() }.items.single()
            assertEquals(newerPreview, domain.lastMessagePreview)
            assertEquals(newerMillis, domain.updatedAt)

            val row = flowValue(db.chatDao().observeConversations(1L)).single()
            assertEquals(newerCipher, row.lastMessagePreviewCipher)
            assertEquals(newerMillis, row.lastMessageAtMillis)
            assertEquals(newerRaw, row.lastMessageAtRaw)
            assertEquals(newerMillis, row.updatedAtMillis)
            assertEquals(newerRaw, row.updatedAtRaw)
        }

        // 服务端返回更新的消息时正常替换预览与时间。
        val latestRaw = "2027-02-01T10:00:00Z"
        val latestMillis = ChatTime.parseMillis(latestRaw)
        val latestContent = "服务端较新消息"
        dataSource.conversations.clear()
        dataSource.conversations += ChatConversationDto(
            id = TEST_CONVERSATION_ID,
            peer = UserDto(
                id = TEST_PEER_ID,
                username = "lin_xiaochu",
                nickname = TEST_PEER_NICKNAME,
                avatar = TEST_PEER_AVATAR
            ),
            lastMessage = ChatMessageDto(
                id = 9L,
                conversationId = TEST_CONVERSATION_ID,
                clientMessageId = "remote-9",
                senderId = TEST_PEER_ID,
                receiverId = 1L,
                type = ChatMessageDto.TYPE_TEXT,
                content = latestContent,
                createdAt = latestRaw
            ),
            updatedAt = latestRaw
        )

        val updated = runBlocking { repository.refreshConversations() }.items.single()
        assertEquals(latestContent, updated.lastMessagePreview)
        assertEquals(latestMillis, updated.updatedAt)

        val updatedRow = flowValue(db.chatDao().observeConversations(1L)).single()
        assertEquals(latestMillis, updatedRow.lastMessageAtMillis)
        assertEquals(latestRaw, updatedRow.lastMessageAtRaw)
        assertEquals(latestMillis, updatedRow.updatedAtMillis)
        assertEquals(latestRaw, updatedRow.updatedAtRaw)
    }

    @Test
    fun `sparse conversation refresh keeps cached peer and last message`() {
        val dataSource = FakeChatDataSource()
        val repository = repository(dataSource)
        runBlocking { repository.refreshConversations() }

        val before = flowValue(db.chatDao().observeConversations(1L)).single()

        dataSource.conversations.clear()
        dataSource.conversations += ChatConversationDto(
            id = TEST_CONVERSATION_ID,
            peer = null,
            lastMessage = null,
            updatedAt = null
        )

        val firstDomain = runBlocking { repository.refreshConversations() }.items.single()
        assertEquals(TEST_PEER_NICKNAME, firstDomain.peerNickname)
        assertEquals(TEST_PEER_AVATAR, firstDomain.peerAvatar)
        assertEquals(TEST_MESSAGE_TWO, firstDomain.lastMessagePreview)
        assertEquals(before.updatedAtMillis, firstDomain.updatedAt)

        val firstSparse = flowValue(db.chatDao().observeConversations(1L)).single()
        assertEquals(before.peerUserId, firstSparse.peerUserId)
        assertEquals(before.peerNicknameCipher, firstSparse.peerNicknameCipher)
        assertEquals(before.peerAvatarCipher, firstSparse.peerAvatarCipher)
        assertEquals(before.lastMessagePreviewCipher, firstSparse.lastMessagePreviewCipher)
        assertEquals(before.lastMessageAtMillis, firstSparse.lastMessageAtMillis)
        assertEquals(before.lastMessageAtRaw, firstSparse.lastMessageAtRaw)
        assertEquals(before.updatedAtMillis, firstSparse.updatedAtMillis)
        assertEquals(before.updatedAtRaw, firstSparse.updatedAtRaw)

        // 第二次缺省刷新仍不得丢失已有数据。
        runBlocking { repository.refreshConversations() }
        val secondSparse = flowValue(db.chatDao().observeConversations(1L)).single()
        assertEquals(before.peerNicknameCipher, secondSparse.peerNicknameCipher)
        assertEquals(before.peerAvatarCipher, secondSparse.peerAvatarCipher)
        assertEquals(before.lastMessagePreviewCipher, secondSparse.lastMessagePreviewCipher)
        assertEquals(before.updatedAtMillis, secondSparse.updatedAtMillis)
    }

    @Test
    fun `cancelled send is marked FAILED and can be retried`() {
        val dataSource = FakeChatDataSource().apply { throwCancellationOnSend = true }
        val repository = repository(dataSource)

        val error = runCatching {
            runBlocking { repository.sendMessage(TEST_CONVERSATION_ID, "取消消息") }
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        val failed = flowValue(
            db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID)
        ).single()
        assertEquals(ChatMessageStatus.FAILED.name, failed.localStatus)
        assertEquals("取消消息", decrypt(failed.contentCipher))

        dataSource.throwCancellationOnSend = false
        val retried = runBlocking { repository.retryMessage(failed.clientMessageId) }
        assertEquals(ChatMessageStatus.SENT, retried.status)
        assertEquals(1, flowValue(db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID)).size)
    }

    @Test
    fun `history pagination keeps previously cached messages`() {
        val dataSource = FakeChatDataSource().apply { messagePageSize = 2 }
        dataSource.messages += ChatMessageDto(
            id = 3L,
            conversationId = TEST_CONVERSATION_ID,
            clientMessageId = "remote-3",
            senderId = TEST_PEER_ID,
            receiverId = 1L,
            type = ChatMessageDto.TYPE_TEXT,
            content = "第三页消息",
            createdAt = "2026-10-20T09:02:00Z"
        )
        val repository = repository(dataSource)

        val latest = runBlocking { repository.refreshMessages(TEST_CONVERSATION_ID) }
        assertEquals(2, latest.items.size)
        assertNotNull(latest.nextCursor)
        assertEquals(2, flowValue(db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID)).size)

        val older = runBlocking {
            repository.refreshMessages(TEST_CONVERSATION_ID, latest.nextCursor)
        }
        assertEquals(1, older.items.size)
        assertNull(older.nextCursor)
        assertEquals(3, flowValue(db.chatDao().observeMessages(1L, TEST_CONVERSATION_ID)).size)
    }

    @Test
    fun `cipher failure refuses to write anything`() {
        val failingCipher = object : ChatCipher {
            override fun encrypt(plaintext: String): String =
                throw ChatCipherException("聊天加密密钥不可用")

            override fun decrypt(encoded: String): ChatDecryption =
                ChatDecryption.Unavailable
        }
        val repository = repository(FakeChatDataSource(), cipher = failingCipher)

        val error = runCatching {
            runBlocking { repository.sendMessage(TEST_CONVERSATION_ID, "明文不能落盘") }
        }.exceptionOrNull()

        assertTrue(error is ChatCipherException)
        assertFalse(error!!.message.orEmpty().contains("明文不能落盘"))
        assertEquals(0, runBlocking { db.chatDao().countMessages(1L) })
    }

    @Test
    fun `decryption failure maps to safe placeholder instead of plaintext`() {
        runBlocking { repository(FakeChatDataSource()).refreshMessages(TEST_CONVERSATION_ID) }

        val repositoryWithWrongKey = repository(
            dataSource = FakeChatDataSource(),
            cipher = newTestCipher()
        )
        val messages = flowValue(repositoryWithWrongKey.observeMessages(TEST_CONVERSATION_ID))

        assertEquals(2, messages.size)
        assertTrue(messages.all { it.content == ChatCipher.UNAVAILABLE_TEXT })
    }

    @Test
    fun `mock failure and latency flow through repository and allow retry`() {
        val mock = MockChatDataSource(
            latencyMillis = 120L,
            failure = ChatMockFailure(ChatMockOperation.SEND_MESSAGE)
        )
        val repository = repository(mock)
        assertTrue(repository.isMock)

        val startedAt = System.nanoTime()
        val error = runCatching {
            runBlocking {
                repository.sendMessage(MockChatDataSource.DEMO_CONVERSATION_ID, "演示失败")
            }
        }.exceptionOrNull()
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L

        assertTrue(error is AuthException)
        assertTrue("expected injected latency, was ${elapsedMillis}ms", elapsedMillis >= 100L)
        val failed = flowValue(
            db.chatDao().observeMessages(1L, MockChatDataSource.DEMO_CONVERSATION_ID)
        ).single()
        assertEquals(ChatMessageStatus.FAILED.name, failed.localStatus)

        mock.setFailure(null)
        mock.setLatencyMillis(0L)
        val retried = runBlocking { repository.retryMessage(failed.clientMessageId) }
        assertEquals(ChatMessageStatus.SENT, retried.status)
        assertEquals(1, flowValue(
            db.chatDao().observeMessages(1L, MockChatDataSource.DEMO_CONVERSATION_ID)
        ).size)
    }

    @Test
    fun `conversation and notification refresh upserts by primary key`() {
        val repository = repository(FakeChatDataSource())
        runBlocking {
            repository.refreshConversations()
            repository.refreshConversations()
            repository.refreshNotifications()
            repository.refreshNotifications()
        }

        assertEquals(1, runBlocking { db.chatDao().countConversations(1L) })
        assertEquals(1, runBlocking { db.chatDao().countNotifications(1L) })
    }

    private fun repository(
        dataSource: ChatDataSource,
        cipher: ChatCipher = this.cipher
    ): ChatRepository = ChatRepository(
        dataSource = dataSource,
        databaseProvider = { db },
        cipher = cipher,
        ownerProvider = ChatOwnerProvider { currentOwner }
    )

    private fun assertEncrypted(cipherText: String?, plaintext: String) {
        assertNotNull(cipherText)
        assertNotEquals(plaintext, cipherText)
        assertFalse("cipher column must not contain plaintext", cipherText!!.contains(plaintext))
        assertEquals(plaintext, decrypt(cipherText))
    }

    private fun decrypt(cipherText: String): String =
        when (val result = cipher.decrypt(cipherText)) {
            is ChatDecryption.Success -> result.value
            ChatDecryption.Unavailable -> throw AssertionError("expected decryptable ciphertext")
        }

    private fun <T> flowValue(flow: Flow<T>): T =
        runBlocking { withTimeout(5_000L) { flow.first() } }

    /** 按调用顺序返回不同 owner，用于稳定构造“复核窗口内切换账号”。 */
    private class SequencedOwnerProvider(vararg owners: Long?) : ChatOwnerProvider {

        private val values = owners.toList()

        @Volatile
        private var index = 0

        override fun currentOwnerId(): Long? {
            val value = values[index.coerceAtMost(values.lastIndex)]
            if (index < values.lastIndex) index++
            return value
        }
    }
}
