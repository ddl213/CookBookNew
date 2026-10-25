package com.rhys.order.data

import com.example.network.AuthException
import com.example.network.bean.CreateOrderItem
import com.example.network.bean.OrderDto
import com.example.network.bean.OrderStatus
import com.example.network.bean.PageResponse
import com.example.network.order.OrderDataSourceKind
import com.rhys.order.data.pending.AesGcmOrderPendingCipher
import com.rhys.order.data.pending.OrderPendingCipher
import com.rhys.order.data.pending.OrderPendingScope
import com.rhys.order.data.pending.OrderPendingStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import javax.crypto.KeyGenerator

/**
 * 创建幂等与超时恢复：pending 保留、原键重放、明确 4xx 清理、
 * 账号/数据源/环境隔离，以及落盘内容不含明文备注。
 */
class OrderRepositoryPendingTest {

    private val store = FakeOrderPendingStore()
    private val dataSource = FakeOrderDataSource()
    private var ownerId: Long? = OWNER_ONE
    private var baseUrl = BASE_URL
    private var nextKey = 0

    private val repository = OrderRepository(
        dataSource = dataSource,
        ownerProvider = OrderOwnerProvider { ownerId },
        pendingStore = store,
        cipher = newTestCipher(),
        idempotencyKeyFactory = { "key-${++nextKey}" },
        baseUrlProvider = { baseUrl },
        clock = { CREATED_AT }
    )

    @Test
    fun `io timeout keeps pending and stores no plaintext note`() = runBlocking {
        dataSource.failure = IOException("timeout")
        val note = "少放辣，不要香菜"

        val error = awaitPending { repository.createOrder(ITEMS, note) }

        assertEquals("key-1", error.idempotencyKey)
        val pending = repository.pendingCreate()
        assertNotNull(pending)
        assertTrue(pending!!.readable)
        assertEquals("key-1", pending.idempotencyKey)
        assertEquals(ITEMS, pending.items)
        assertEquals(note, pending.note)
        assertEquals(CREATED_AT, pending.createdAt)

        val stored = store.values.values.single()
        assertTrue(stored.startsWith("key-1|"))
        assertFalse(stored.contains(note))
        assertFalse(stored.contains("少放辣"))
    }

    @Test
    fun `5xx keeps pending as result unknown`() = runBlocking {
        dataSource.failure = AuthException("INTERNAL_ERROR", "server error", 500)

        awaitPending { repository.createOrder(ITEMS, null) }

        assertNotNull(repository.pendingCreate())
        assertEquals(1, dataSource.calls.size)
    }

    @Test
    fun `401 is definite rejection and clears pending`() = runBlocking {
        dataSource.failure = AuthException("UNAUTHORIZED", "登录已失效", 401)

        val error = awaitAuth { repository.createOrder(ITEMS, null) }

        assertEquals("UNAUTHORIZED", error.code)
        assertNull(repository.pendingCreate())
    }

    @Test
    fun `definite 4xx clears pending`() = runBlocking {
        dataSource.failure = AuthException("NOT_FRIENDS", "不是好友", 403)

        val error = awaitAuth { repository.createOrder(ITEMS, null) }

        assertEquals("NOT_FRIENDS", error.code)
        assertNull(repository.pendingCreate())
        assertEquals(0, store.values.size)
    }

    @Test
    fun `success returns server order and clears pending`() = runBlocking {
        val order = repository.createOrder(ITEMS, "少放辣")

        assertEquals(SERVER_ORDER_ID, order.id)
        assertNull(repository.pendingCreate())
        assertEquals(1, dataSource.calls.size)
        assertEquals("key-1", dataSource.calls.single().idempotencyKey)
        assertEquals(ITEMS, dataSource.calls.single().items)
        assertEquals("少放辣", dataSource.calls.single().note)
    }

    @Test
    fun `retry replays original key content and clears on success`() = runBlocking {
        dataSource.failure = IOException("timeout")
        awaitPending { repository.createOrder(ITEMS, "少放辣") }
        dataSource.failure = null

        val order = repository.retryPendingCreate()

        assertEquals(SERVER_ORDER_ID, order.id)
        assertEquals(2, dataSource.calls.size)
        assertEquals(dataSource.calls[0].idempotencyKey, dataSource.calls[1].idempotencyKey)
        assertEquals(dataSource.calls[0].items, dataSource.calls[1].items)
        assertEquals(dataSource.calls[0].note, dataSource.calls[1].note)
        assertNull(repository.pendingCreate())
    }

    @Test
    fun `retry without pending reports not found`() = runBlocking {
        val error = awaitAuth { repository.retryPendingCreate() }
        assertEquals("ORDER_PENDING_NOT_FOUND", error.code)
        assertEquals(404, error.httpStatus)
    }

    @Test
    fun `same content reuses key instead of creating a new one`() = runBlocking {
        dataSource.failure = IOException("timeout")
        awaitPending { repository.createOrder(ITEMS, "少放辣") }
        dataSource.failure = null

        repository.createOrder(ITEMS, "少放辣")

        assertEquals(2, dataSource.calls.size)
        assertEquals(dataSource.calls[0].idempotencyKey, dataSource.calls[1].idempotencyKey)
        assertEquals("key-1", dataSource.calls[1].idempotencyKey)
        assertNull(repository.pendingCreate())
    }

    @Test
    fun `different content while pending is rejected without sending`() = runBlocking {
        dataSource.failure = IOException("timeout")
        awaitPending { repository.createOrder(ITEMS, "少放辣") }
        dataSource.failure = null

        val error = awaitConflict { repository.createOrder(ITEMS, "多放辣") }

        assertEquals("key-1", error.pending.idempotencyKey)
        assertEquals(1, dataSource.calls.size)
        assertEquals("少放辣", repository.pendingCreate()!!.note)
    }

    @Test
    fun `unreadable pending blocks create and can be discarded`() = runBlocking {
        val scope = OrderPendingScope(OWNER_ONE, OrderDataSourceKind.REMOTE, BASE_URL)
        store.write(scope, "key-x|not-a-valid-ciphertext")

        val pending = repository.pendingCreate()
        assertNotNull(pending)
        assertFalse(pending!!.readable)
        assertEquals("key-x", pending.idempotencyKey)

        val error = awaitConflict { repository.createOrder(ITEMS, null) }
        assertEquals("key-x", error.pending.idempotencyKey)
        assertEquals(0, dataSource.calls.size)

        repository.discardPending()
        assertNull(repository.pendingCreate())
    }

    @Test
    fun `pending is isolated by owner`() = runBlocking {
        dataSource.failure = IOException("timeout")
        awaitPending { repository.createOrder(ITEMS, "owner-one") }

        ownerId = OWNER_TWO
        assertNull(repository.pendingCreate())
        awaitPending { repository.createOrder(ITEMS, "owner-two") }
        assertEquals(2, store.values.size)

        ownerId = OWNER_ONE
        dataSource.failure = null
        repository.retryPendingCreate()
        assertEquals("key-1", dataSource.calls.last().idempotencyKey)
    }

    @Test
    fun `pending is isolated by data source kind`() = runBlocking {
        val sharedStore = FakeOrderPendingStore()
        val cipher = newTestCipher()
        val mockSource = FakeOrderDataSource(isMock = true)
        val remoteSource = FakeOrderDataSource(isMock = false)
        val mockRepository = OrderRepository(
            dataSource = mockSource,
            ownerProvider = OrderOwnerProvider { OWNER_ONE },
            pendingStore = sharedStore,
            cipher = cipher,
            idempotencyKeyFactory = { "mock-key" },
            baseUrlProvider = { BASE_URL },
            clock = { CREATED_AT }
        )
        val remoteRepository = OrderRepository(
            dataSource = remoteSource,
            ownerProvider = OrderOwnerProvider { OWNER_ONE },
            pendingStore = sharedStore,
            cipher = cipher,
            idempotencyKeyFactory = { "remote-key" },
            baseUrlProvider = { BASE_URL },
            clock = { CREATED_AT }
        )

        mockSource.failure = IOException("timeout")
        awaitPending { mockRepository.createOrder(ITEMS, null) }

        assertNotNull(mockRepository.pendingCreate())
        assertNull(remoteRepository.pendingCreate())

        remoteSource.failure = IOException("timeout")
        awaitPending { remoteRepository.createOrder(ITEMS, null) }
        assertEquals(2, sharedStore.values.size)
        assertEquals("mock-key", mockRepository.pendingCreate()!!.idempotencyKey)
        assertEquals("remote-key", remoteRepository.pendingCreate()!!.idempotencyKey)
    }

    @Test
    fun `pending is isolated by backend environment`() = runBlocking {
        dataSource.failure = IOException("timeout")
        awaitPending { repository.createOrder(ITEMS, "env-one") }

        baseUrl = "http://other.example.com/api/v1/"
        assertNull(repository.pendingCreate())
        awaitPending { repository.createOrder(ITEMS, "env-two") }
        assertEquals(2, store.values.size)

        baseUrl = BASE_URL
        assertNotNull(repository.pendingCreate())
        assertEquals("key-1", repository.pendingCreate()!!.idempotencyKey)
    }

    @Test
    fun `discard pending clears current scope`() = runBlocking {
        dataSource.failure = IOException("timeout")
        awaitPending { repository.createOrder(ITEMS, null) }
        assertNotNull(repository.pendingCreate())

        repository.discardPending()

        assertNull(repository.pendingCreate())
    }

    @Test
    fun `cancellation keeps pending for later confirmation`() = runBlocking {
        dataSource.failure = CancellationException("cancelled")

        try {
            repository.createOrder(ITEMS, null)
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            assertNotNull(e)
        }

        assertNotNull(repository.pendingCreate())
    }

    @Test
    fun `logged out pending access returns null and does not read store`() {
        ownerId = null
        assertNull(repository.pendingCreate())
        repository.discardPending()
    }

    private suspend fun awaitPending(
        block: suspend () -> Unit
    ): OrderCreatePendingException {
        try {
            block()
            fail("expected OrderCreatePendingException")
            error("unreachable")
        } catch (e: OrderCreatePendingException) {
            return e
        }
    }

    private suspend fun awaitAuth(block: suspend () -> Unit): AuthException {
        try {
            block()
            fail("expected AuthException")
            error("unreachable")
        } catch (e: AuthException) {
            return e
        }
    }

    private suspend fun awaitConflict(
        block: suspend () -> Unit
    ): OrderPendingConflictException {
        try {
            block()
            fail("expected OrderPendingConflictException")
            error("unreachable")
        } catch (e: OrderPendingConflictException) {
            return e
        }
    }

    private class FakeOrderPendingStore : OrderPendingStore {
        val values = mutableMapOf<OrderPendingScope, String>()

        override fun read(scope: OrderPendingScope): String? = values[scope]

        override fun write(scope: OrderPendingScope, payload: String) {
            values[scope] = payload
        }

        override fun clear(scope: OrderPendingScope) {
            values.remove(scope)
        }
    }

    private class FakeOrderDataSource(
        override val isMock: Boolean = false
    ) : OrderDataSource {

        val calls = mutableListOf<CreateCall>()
        var failure: Throwable? = null

        override suspend fun createOrder(
            items: List<CreateOrderItem>,
            note: String?,
            idempotencyKey: String,
            demoContext: OrderDemoContext?
        ): OrderDto {
            calls += CreateCall(items = items, note = note, idempotencyKey = idempotencyKey)
            failure?.let { throw it }
            return OrderDto(id = SERVER_ORDER_ID, status = OrderStatus.PENDING_ACCEPT)
        }

        override suspend fun listOrders(
            role: String,
            status: String?,
            page: Int,
            pageSize: Int
        ): PageResponse<OrderDto> = PageResponse()

        override suspend fun getOrder(orderId: Long): OrderDto =
            OrderDto(id = orderId, status = OrderStatus.PENDING_ACCEPT)

        override suspend fun actOnItem(
            orderId: Long,
            itemId: Long,
            action: String,
            expectedVersion: Long
        ): OrderDto = OrderDto(id = orderId, status = OrderStatus.PENDING_ACCEPT)

        override suspend fun cancelOrder(orderId: Long, expectedVersion: Long): OrderDto =
            OrderDto(id = orderId, status = OrderStatus.CANCELLED)
    }

    private data class CreateCall(
        val items: List<CreateOrderItem>,
        val note: String?,
        val idempotencyKey: String
    )

    private companion object {
        const val OWNER_ONE = 1L
        const val OWNER_TWO = 2L
        const val BASE_URL = "http://localhost:3000/api/v1/"
        const val CREATED_AT = 1_760_000_000_000L
        const val SERVER_ORDER_ID = 9001L

        val ITEMS = listOf(
            CreateOrderItem(recipeId = 101L, quantity = 2),
            CreateOrderItem(recipeId = 102L, quantity = 1)
        )

        fun newTestCipher(): OrderPendingCipher {
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            return AesGcmOrderPendingCipher { key }
        }
    }
}
