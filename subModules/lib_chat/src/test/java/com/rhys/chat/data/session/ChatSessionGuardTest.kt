package com.rhys.chat.data.session

import com.example.network.bean.UserDto
import com.example.network.session.AuthState
import com.rhys.chat.data.ChatOwnerProvider
import com.rhys.chat.data.ChatRepository
import com.rhys.chat.data.FakeOwnerStore
import com.rhys.chat.data.MockChatDataSource
import com.rhys.chat.data.TEST_CONVERSATION_ID
import com.rhys.chat.data.local.ChatDatabase
import com.rhys.chat.data.newTestCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 登录态守卫：账号隔离、明确退出清理、Unknown/同账号不清理。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatSessionGuardTest {

    private lateinit var db: ChatDatabase
    private val repository = ChatRepository(
        dataSource = MockChatDataSource(),
        databaseProvider = { db },
        cipher = newTestCipher(),
        ownerProvider = ChatOwnerProvider { 1L }
    )

    @Before
    fun setUp() {
        db = ChatDatabase.buildInMemory(RuntimeEnvironment.getApplication())
        runBlocking {
            repository.refreshConversations()
            repository.refreshMessages(TEST_CONVERSATION_ID)
            repository.refreshNotifications()
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `logged in records owner without clearing existing cache`() = runBlocking {
        val store = FakeOwnerStore()
        val guard = guard(store)

        guard.handle(AuthState.LoggedIn(user(1L)))

        assertEquals(1L, store.ownerId)
        assertCachePresent()
    }

    @Test
    fun `same account does not clear cache`() = runBlocking {
        val store = FakeOwnerStore(initialOwnerId = 1L)
        val guard = guard(store)

        guard.handle(AuthState.LoggedIn(user(1L)))

        assertEquals(1L, store.ownerId)
        assertCachePresent()
    }

    @Test
    fun `explicit logout clears all cache and forgets owner`() = runBlocking {
        val store = FakeOwnerStore(initialOwnerId = 1L)
        val guard = guard(store, token = null)

        guard.handle(AuthState.LoggedOut)

        assertNull(store.ownerId)
        assertCacheEmpty()
    }

    @Test
    fun `logged out with token still present does not clear cache`() = runBlocking {
        val store = FakeOwnerStore(initialOwnerId = 1L)
        val guard = guard(store, token = "still-valid")

        guard.handle(AuthState.LoggedOut)

        assertEquals(1L, store.ownerId)
        assertCachePresent()
    }

    @Test
    fun `unknown state never clears cache`() = runBlocking {
        val store = FakeOwnerStore(initialOwnerId = 1L)
        val guard = guard(store, token = null)

        guard.handle(AuthState.Unknown)

        assertEquals(1L, store.ownerId)
        assertCachePresent()
    }

    @Test
    fun `account switch clears old cache and records new owner`() = runBlocking {
        val store = FakeOwnerStore(initialOwnerId = 1L)
        val guard = guard(store)

        guard.handle(AuthState.LoggedIn(user(2L)))

        assertEquals(2L, store.ownerId)
        assertCacheEmpty()
    }

    @Test
    fun `start collects the state flow and applies cleanup`() = runBlocking {
        val store = FakeOwnerStore()
        val states = MutableStateFlow<AuthState>(AuthState.Unknown)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val guard = ChatSessionGuard(
            scope = scope,
            databaseProvider = { db },
            ownerStore = store,
            states = states,
            currentToken = { null },
            onError = { throw AssertionError(it) }
        )
        val job = guard.start()

        assertEquals(3L, db.chatDao().countMessages(1L).toLong())

        states.value = AuthState.LoggedIn(user(1L))
        await { store.ownerId == 1L && db.chatDao().countMessages(1L) == 3 }

        states.value = AuthState.LoggedOut
        await { store.ownerId == null && db.chatDao().countMessages(1L) == 0 }

        job.cancel()
        scope.cancel()
    }

    @Test
    fun `cleanup errors do not escape the guard`() = runBlocking {
        val store = FakeOwnerStore(initialOwnerId = 1L)
        val failures = mutableListOf<Throwable>()
        val states = MutableStateFlow<AuthState>(AuthState.Unknown)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val guard = ChatSessionGuard(
            scope = scope,
            databaseProvider = { throw IllegalStateException("db unavailable") },
            ownerStore = store,
            states = states,
            currentToken = { null },
            onError = { failures += it }
        )
        val job = guard.start()

        states.value = AuthState.LoggedOut

        assertEquals(1, failures.size)
        job.cancel()
        scope.cancel()
    }

    private fun guard(
        store: FakeOwnerStore,
        token: String? = null
    ): ChatSessionGuard = ChatSessionGuard(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        databaseProvider = { db },
        ownerStore = store,
        states = emptyFlow(),
        currentToken = { token },
        onError = { throw AssertionError(it) }
    )

    private suspend fun assertCachePresent() {
        assertEquals(1, db.chatDao().countConversations(1L))
        assertEquals(3, db.chatDao().countMessages(1L))
        assertEquals(1, db.chatDao().countNotifications(1L))
    }

    private suspend fun assertCacheEmpty() {
        assertEquals(0, db.chatDao().countConversations(1L))
        assertEquals(0, db.chatDao().countMessages(1L))
        assertEquals(0, db.chatDao().countNotifications(1L))
    }

    private fun user(id: Long) = UserDto(
        id = id,
        username = "user$id",
        nickname = "用户$id"
    )

    /** 守卫在 Room 挂起调用处会让出线程，测试需等待清理完成。 */
    private suspend fun await(condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            kotlinx.coroutines.delay(10L)
        }
        throw AssertionError("guard action did not complete in time")
    }
}
