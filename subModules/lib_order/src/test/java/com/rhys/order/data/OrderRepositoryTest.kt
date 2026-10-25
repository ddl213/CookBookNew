package com.rhys.order.data

import com.example.network.bean.CreateOrderItem
import com.example.network.bean.OrderDto
import com.example.network.bean.OrderStatus
import com.example.network.bean.PageResponse
import com.rhys.order.data.pending.AesGcmOrderPendingCipher
import com.rhys.order.data.pending.OrderPendingCipher
import com.rhys.order.data.pending.OrderPendingScope
import com.rhys.order.data.pending.OrderPendingStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import javax.crypto.KeyGenerator

/** Repository 的演示标识、数据源委托与失败不回退。 */
class OrderRepositoryTest {

    private val dataSource = RecordingOrderDataSource()
    private val repository = OrderRepository(
        dataSource = dataSource,
        ownerProvider = OrderOwnerProvider { 1L },
        pendingStore = EmptyPendingStore,
        cipher = newTestCipher(),
        idempotencyKeyFactory = { "key-1" },
        baseUrlProvider = { "http://localhost:3000/api/v1/" }
    )

    @Test
    fun `isMock delegates to data source`() {
        assertFalse(repository.isMock)
        dataSource.mock = true
        assertTrue(repository.isMock)
    }

    @Test
    fun `list get act and cancel delegate to data source`() = runBlocking {
        val page = repository.listOrders(
            role = OrderDataSource.ROLE_SELLER,
            status = OrderStatus.ACCEPTED,
            page = 2,
            pageSize = 10
        )
        assertEquals(42L, page.items.single().id)
        assertEquals(listOf("seller", OrderStatus.ACCEPTED, 2, 10), dataSource.listCalls.single())

        val detail = repository.getOrder(42L)
        assertEquals(42L, detail.id)
        assertEquals(listOf(42L), dataSource.getCalls)

        val acted = repository.actOnItem(42L, 4201L, "accept", 3L)
        assertEquals(42L, acted.id)
        assertEquals(listOf(ActionCall(42L, 4201L, "accept", 3L)), dataSource.actionCalls)

        val cancelled = repository.cancelOrder(42L, 5L)
        assertEquals(OrderStatus.CANCELLED, cancelled.status)
        assertEquals(listOf(42L to 5L), dataSource.cancelCalls)
    }

    @Test
    fun `remote failure never fabricates a success result`() = runBlocking {
        dataSource.createFailure = IOException("offline")

        try {
            repository.createOrder(
                items = listOf(CreateOrderItem(101L, 1)),
                note = null
            )
            org.junit.Assert.fail("expected OrderCreatePendingException")
        } catch (e: OrderCreatePendingException) {
            assertEquals("key-1", e.idempotencyKey)
        }
    }

    private class RecordingOrderDataSource : OrderDataSource {
        var mock: Boolean = false
        override val isMock: Boolean
            get() = mock

        val listCalls = mutableListOf<List<Any?>>()
        val getCalls = mutableListOf<Long>()
        val actionCalls = mutableListOf<ActionCall>()
        val cancelCalls = mutableListOf<Pair<Long, Long>>()
        var createFailure: Throwable? = null

        override suspend fun createOrder(
            items: List<CreateOrderItem>,
            note: String?,
            idempotencyKey: String,
            demoContext: OrderDemoContext?
        ): OrderDto {
            createFailure?.let { throw it }
            return OrderDto(id = 42L, status = OrderStatus.PENDING_ACCEPT)
        }

        override suspend fun listOrders(
            role: String,
            status: String?,
            page: Int,
            pageSize: Int
        ): PageResponse<OrderDto> {
            listCalls += listOf(role, status, page, pageSize)
            return PageResponse(items = listOf(OrderDto(id = 42L)), total = 1)
        }

        override suspend fun getOrder(orderId: Long): OrderDto {
            getCalls += orderId
            return OrderDto(id = orderId, status = OrderStatus.PENDING_ACCEPT)
        }

        override suspend fun actOnItem(
            orderId: Long,
            itemId: Long,
            action: String,
            expectedVersion: Long
        ): OrderDto {
            actionCalls += ActionCall(orderId, itemId, action, expectedVersion)
            return OrderDto(id = orderId, status = OrderStatus.PENDING_ACCEPT)
        }

        override suspend fun cancelOrder(orderId: Long, expectedVersion: Long): OrderDto {
            cancelCalls += orderId to expectedVersion
            return OrderDto(id = orderId, status = OrderStatus.CANCELLED)
        }
    }

    private data class ActionCall(
        val orderId: Long,
        val itemId: Long,
        val action: String,
        val expectedVersion: Long
    )

    private object EmptyPendingStore : OrderPendingStore {
        override fun read(scope: OrderPendingScope): String? = null

        override fun write(scope: OrderPendingScope, payload: String) = Unit

        override fun clear(scope: OrderPendingScope) = Unit
    }

    private companion object {
        fun newTestCipher(): OrderPendingCipher {
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            return AesGcmOrderPendingCipher { key }
        }
    }
}
