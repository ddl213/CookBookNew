package com.rhys.order.data

import com.example.network.bean.CreateOrderItem
import com.example.network.bean.OrderDto
import com.example.network.bean.PageResponse
import com.example.network.order.OrderDataSourceKind
import com.example.network.order.OrderDataSourceResolution
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 工厂按共享配置选择数据源；非法配置回退 remote 并发出 warning。 */
class OrderDataSourceFactoryTest {

    private val mock = StubDataSource(isMock = true)
    private val remote = StubDataSource(isMock = false)

    @Test
    fun `mock resolution selects mock factory without warning`() {
        val warnings = mutableListOf<String>()
        val created = OrderDataSourceFactory.create(
            resolution = OrderDataSourceResolution(OrderDataSourceKind.MOCK, "mock", false),
            onWarning = { warnings += it },
            mockFactory = { mock },
            remoteFactory = { remote }
        )

        assertSame(mock, created)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `remote resolution selects remote factory`() {
        val created = OrderDataSourceFactory.create(
            resolution = OrderDataSourceResolution(OrderDataSourceKind.REMOTE, "remote", false),
            onWarning = {},
            mockFactory = { mock },
            remoteFactory = { remote }
        )

        assertSame(remote, created)
    }

    @Test
    fun `invalid value selects remote and reports warning`() {
        val warnings = mutableListOf<String>()
        val created = OrderDataSourceFactory.create(
            resolution = OrderDataSourceResolution(OrderDataSourceKind.REMOTE, "bogus", true),
            onWarning = { warnings += it },
            mockFactory = { mock },
            remoteFactory = { remote }
        )

        assertSame(remote, created)
        assertTrue(warnings.single().contains("bogus"))
        assertTrue(warnings.single().contains("remote"))
    }

    private class StubDataSource(override val isMock: Boolean) : OrderDataSource {

        override suspend fun createOrder(
            items: List<CreateOrderItem>,
            note: String?,
            idempotencyKey: String,
            demoContext: OrderDemoContext?
        ): OrderDto = OrderDto()

        override suspend fun listOrders(
            role: String,
            status: String?,
            page: Int,
            pageSize: Int
        ): PageResponse<OrderDto> = PageResponse()

        override suspend fun getOrder(orderId: Long): OrderDto = OrderDto()

        override suspend fun actOnItem(
            orderId: Long,
            itemId: Long,
            action: String,
            expectedVersion: Long
        ): OrderDto = OrderDto()

        override suspend fun cancelOrder(orderId: Long, expectedVersion: Long): OrderDto =
            OrderDto()
    }
}
