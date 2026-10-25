package com.rhys.order.data

import com.example.network.bean.OrderPartyDto

/**
 * Mock 订单的进程内共享存储。
 *
 * 默认 [shared] 由整个 App 进程共用：同一进程内切换登录账号后，
 * 买方与卖方仍能看到同一订单。测试可创建独立实例隔离。
 * 数据只存在内存，不代表真实后端同步。
 */
class MockOrderStore {

    internal val lock = Any()
    internal val orders = linkedMapOf<Long, MockOrderRecord>()
    internal val idempotency = mutableMapOf<String, MockIdempotencyRecord>()
    internal var nextOrderId: Long = MOCK_ORDER_BASE_ID

    /** 清空全部 Mock 数据并重置下一个订单 ID；仅供测试/演示重置。 */
    fun clear() {
        synchronized(lock) {
            orders.clear()
            idempotency.clear()
            nextOrderId = MOCK_ORDER_BASE_ID
        }
    }

    companion object {
        const val MOCK_ORDER_BASE_ID = 5000L

        /** 首个演示订单 ID，之后按顺序递增；确定性便于演示与测试。 */
        const val FIRST_ORDER_ID = MOCK_ORDER_BASE_ID + 1

        /** 进程级共享实例。 */
        val shared: MockOrderStore by lazy { MockOrderStore() }

        /** 测试用：清空进程级共享数据。 */
        fun resetSharedForTests() {
            shared.clear()
        }
    }
}

internal data class MockOrderRecord(
    val id: Long,
    val buyer: OrderPartyDto,
    val seller: OrderPartyDto,
    val note: String?,
    var version: Long,
    val items: MutableList<MockOrderItemRecord>,
    val createdAt: String,
    var updatedAt: String
)

internal data class MockOrderItemRecord(
    val id: Long,
    val recipeId: Long,
    val recipeName: String,
    val imageUrl: String,
    val quantity: Int,
    var status: String,
    var version: Long,
    val createdAt: String,
    var updatedAt: String
)

internal data class MockIdempotencyRecord(
    val buyerId: Long,
    val fingerprint: String,
    val orderId: Long
)
