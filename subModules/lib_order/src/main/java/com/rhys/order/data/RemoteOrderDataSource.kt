package com.rhys.order.data

import com.example.network.ApiClient
import com.example.network.OrderErrorMapper
import com.example.network.api.OrderApi
import com.example.network.bean.CreateOrderBody
import com.example.network.bean.CreateOrderItem
import com.example.network.bean.ExpectedVersionBody
import com.example.network.bean.OrderDto
import com.example.network.bean.PageResponse
import kotlinx.coroutines.CancellationException

/**
 * 基于统一 [ApiClient] 的真实订单数据源。
 *
 * 只做参数归一化、接口调用与错误转换；好友关系、参与方、动作权限、
 * 版本冲突与幂等由服务端判定。失败不会回退 Mock 或伪造成功。
 * 构造参数可注入 [OrderApi] 以便测试。
 */
class RemoteOrderDataSource(
    private val orderApi: OrderApi = ApiClient.orderApi
) : OrderDataSource {

    override val isMock: Boolean = false

    override suspend fun createOrder(
        items: List<CreateOrderItem>,
        note: String?,
        idempotencyKey: String,
        demoContext: OrderDemoContext?
    ): OrderDto {
        val normalized = OrderDataSource.normalizedCreate(items, note)
        val safeKey = OrderDataSource.normalizedIdempotencyKey(idempotencyKey)
        // Remote 不读取 demoContext；演示上下文只属于 Mock。
        return call {
            orderApi.createOrder(
                idempotencyKey = safeKey,
                body = CreateOrderBody(items = normalized.items, note = normalized.note)
            )
        }
    }

    override suspend fun listOrders(
        role: String,
        status: String?,
        page: Int,
        pageSize: Int
    ): PageResponse<OrderDto> = call {
        orderApi.listOrders(
            role = OrderDataSource.normalizedRole(role),
            status = OrderDataSource.normalizedStatus(status),
            page = OrderDataSource.normalizedPage(page),
            pageSize = OrderDataSource.normalizedPageSize(pageSize)
        )
    }

    override suspend fun getOrder(orderId: Long): OrderDto {
        require(orderId > 0) { "orderId must be positive" }
        return call { orderApi.getOrder(orderId) }
    }

    override suspend fun actOnItem(
        orderId: Long,
        itemId: Long,
        action: String,
        expectedVersion: Long
    ): OrderDto {
        require(orderId > 0) { "orderId must be positive" }
        require(itemId > 0) { "itemId must be positive" }
        require(expectedVersion > 0) { "expectedVersion must be positive" }
        val safeAction = OrderDataSource.normalizedItemAction(action)
        return call {
            orderApi.actOnItem(
                orderId = orderId,
                itemId = itemId,
                action = safeAction,
                body = ExpectedVersionBody(expectedVersion)
            )
        }
    }

    override suspend fun cancelOrder(orderId: Long, expectedVersion: Long): OrderDto {
        require(orderId > 0) { "orderId must be positive" }
        require(expectedVersion > 0) { "expectedVersion must be positive" }
        return call {
            orderApi.cancelOrder(
                orderId = orderId,
                body = ExpectedVersionBody(expectedVersion)
            )
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw OrderErrorMapper.map(e, unauthorizedMessage = UNAUTHORIZED_MESSAGE)
        }
    }

    private companion object {
        const val UNAUTHORIZED_MESSAGE = "登录已失效，请重新登录"
    }
}
