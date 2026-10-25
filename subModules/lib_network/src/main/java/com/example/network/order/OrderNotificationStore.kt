package com.example.network.order

/**
 * Mock 订单通知事件的进程内共享存储。
 *
 * 位置说明：放在 `lib_network`，使 `lib_order`（写入）与 `lib_chat`（P08-T05 读取）
 * 都能依赖同一契约，而不形成 `lib_order` ↔ `lib_chat` 循环依赖。
 * 本接口只承载 Mock 事件；Remote 通知仍由 `NotificationApi` 按后端契约获取。
 */

/** 契约 §5 的六类订单通知。 */
object OrderNotificationType {
    const val ORDER_CREATED = "order_created"
    const val ORDER_ACCEPTED = "order_accepted"
    const val ORDER_REJECTED = "order_rejected"
    const val ORDER_READY = "order_ready"
    const val ORDER_CANCELLED = "order_cancelled"
    const val ORDER_COMPLETED = "order_completed"

    val ALL: Set<String> = setOf(
        ORDER_CREATED,
        ORDER_ACCEPTED,
        ORDER_REJECTED,
        ORDER_READY,
        ORDER_CANCELLED,
        ORDER_COMPLETED
    )
}

data class OrderNotificationActor(
    val id: Long,
    val nickname: String
)

data class OrderNotificationItem(
    val itemId: Long,
    val recipeId: Long?,
    val recipeName: String,
    val quantity: Int,
    val fromStatus: String?,
    val toStatus: String
)

/**
 * 一条订单通知事件，字段对齐契约 §5 的 schemaVersion 2 payload，
 * 额外包含接收方 ID 与本地事件 ID，便于 `lib_chat` 适配。
 */
data class OrderNotificationEvent(
    val id: Long = 0L,
    val schemaVersion: Int = 2,
    val type: String,
    val orderId: Long,
    val orderStatus: String,
    val actor: OrderNotificationActor,
    val recipientId: Long,
    val items: List<OrderNotificationItem>,
    val createdAt: String
)

/**
 * 通知存储边界。`append` 由存储分配事件 ID；`list` 返回指定接收方的最新事件。
 * 仅用于 Mock；不得把该存储当作后端通知或已读状态来源。
 */
interface OrderNotificationStore {

    fun append(event: OrderNotificationEvent): OrderNotificationEvent

    fun list(recipientId: Long, limit: Int = DEFAULT_LIMIT): List<OrderNotificationEvent>

    fun clear()

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 200
    }
}

/**
 * 内存实现。默认 [shared] 供整个 App 进程共享，使同一进程内切换登录账号后
 * 仍能读取对方的演示通知；测试可创建独立实例隔离。
 */
class InMemoryOrderNotificationStore(
    private val idBase: Long = MOCK_NOTIFICATION_BASE_ID
) : OrderNotificationStore {

    private val lock = Any()
    private val events = mutableListOf<OrderNotificationEvent>()
    private var nextId: Long = idBase

    override fun append(event: OrderNotificationEvent): OrderNotificationEvent {
        synchronized(lock) {
            val stored = event.copy(id = nextId++)
            events += stored
            return stored
        }
    }

    override fun list(recipientId: Long, limit: Int): List<OrderNotificationEvent> {
        if (recipientId <= 0L) return emptyList()
        val safeLimit = limit.coerceIn(1, OrderNotificationStore.MAX_LIMIT)
        return synchronized(lock) {
            events.asReversed()
                .filter { it.recipientId == recipientId }
                .take(safeLimit)
        }
    }

    override fun clear() {
        synchronized(lock) {
            events.clear()
            nextId = idBase
        }
    }

    companion object {
        /** Mock 通知 ID 起始值，与远端通知 ID 区间错开，避免适配时碰撞。 */
        const val MOCK_NOTIFICATION_BASE_ID = 800_000L

        /** 进程级共享实例。 */
        val shared: InMemoryOrderNotificationStore by lazy { InMemoryOrderNotificationStore() }
    }
}
