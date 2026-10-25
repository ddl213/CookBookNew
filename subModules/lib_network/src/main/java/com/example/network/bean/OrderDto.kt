package com.example.network.bean

/**
 * 订单响应模型，字段对齐 P08 订单契约 §2.2。
 *
 * `status` 与 `allowedActions` 以字符串保存，未知值原样保留：
 * 客户端不得用枚举 `valueOf` 解析、不得因未知状态默认放行操作。
 * 是否可执行动作最终由服务端判定。
 */
data class OrderDto(
    val id: Long = 0L,
    val status: String = "",
    val version: Long = 0L,
    val note: String? = null,
    val buyer: OrderPartyDto = OrderPartyDto(),
    val seller: OrderPartyDto = OrderPartyDto(),
    val items: List<OrderItemDto> = emptyList(),
    val itemCounts: OrderItemCountsDto = OrderItemCountsDto(),
    val totalQuantity: Long = 0L,
    val allowedActions: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/** 订单参与方快照；名称在创建订单时固化，后续改昵称不影响订单展示。 */
data class OrderPartyDto(
    val id: Long = 0L,
    val username: String = "",
    val nickname: String = ""
) {
    val displayName: String
        get() = nickname.ifBlank { username }
}

/**
 * 订单明细。`recipe.id` 在菜谱删除后可为空；名称与图片引用仍是快照。
 * `allowedActions` 为空表示当前访问者不能对该明细执行操作。
 */
data class OrderItemDto(
    val id: Long = 0L,
    val recipe: OrderRecipeDto = OrderRecipeDto(),
    val quantity: Int = 0,
    val status: String = "",
    val version: Long = 0L,
    val allowedActions: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/** 菜谱快照；`id` 可空，`imageUrl` 可为空字符串，缺图由 UI 占位。 */
data class OrderRecipeDto(
    val id: Long? = null,
    val name: String = "",
    val imageUrl: String = ""
)

/** 各状态明细条数，全部字段由服务端返回，缺省按 0 处理。总份数另见 [OrderDto.totalQuantity]。 */
data class OrderItemCountsDto(
    val total: Int = 0,
    val pendingAccept: Int = 0,
    val accepted: Int = 0,
    val waitingPickup: Int = 0,
    val completed: Int = 0,
    val rejected: Int = 0,
    val cancelled: Int = 0
)

/** 创建订单请求体；`note` 为空时省略（Gson 默认不序列化 null）。 */
data class CreateOrderBody(
    val items: List<CreateOrderItem>,
    val note: String? = null
)

/** 创建订单的单道菜请求；服务端会再次合并重复菜品并校验结果。 */
data class CreateOrderItem(
    val recipeId: Long,
    val quantity: Int
)

/** 明细动作与整单取消的乐观版本校验请求体。 */
data class ExpectedVersionBody(
    val expectedVersion: Long
)

/**
 * 明细状态与整单汇总状态常量。
 *
 * 明细为六态（不含 [CLOSED]）；整单汇总另含 [CLOSED]（拒绝与取消混合）。
 * 所有判断均使用字符串比较，遇到未知值不崩溃、不推断可操作。
 */
object OrderStatus {
    const val PENDING_ACCEPT = "PENDING_ACCEPT"
    const val ACCEPTED = "ACCEPTED"
    const val REJECTED = "REJECTED"
    const val WAITING_PICKUP = "WAITING_PICKUP"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"

    /** 仅属于整单汇总，不属于单条明细。 */
    const val CLOSED = "CLOSED"

    val ITEM_STATUSES: Set<String> = setOf(
        PENDING_ACCEPT,
        ACCEPTED,
        REJECTED,
        WAITING_PICKUP,
        COMPLETED,
        CANCELLED
    )

    val ALL: Set<String> = ITEM_STATUSES + CLOSED

    /** 明细终态，不可恢复。 */
    val TERMINAL_ITEM_STATUSES: Set<String> = setOf(REJECTED, CANCELLED, COMPLETED)

    fun isKnown(value: String?): Boolean = value != null && value in ALL

    fun isKnownItemStatus(value: String?): Boolean = value != null && value in ITEM_STATUSES

    fun isTerminalItemStatus(value: String?): Boolean =
        value != null && value in TERMINAL_ITEM_STATUSES
}

/**
 * 操作动作常量。
 *
 * 明细动作仅允许 [ITEM_ACTIONS]；整单取消使用 [CANCEL_ALL]，不依赖 `cancel` 推断。
 */
object OrderAction {
    const val ACCEPT = "accept"
    const val REJECT = "reject"
    const val READY = "ready"
    const val CANCEL = "cancel"
    const val COMPLETE = "complete"

    /** 整单取消；只允许出现在订单级 allowedActions。 */
    const val CANCEL_ALL = "cancelAll"

    val ITEM_ACTIONS: Set<String> = setOf(ACCEPT, REJECT, READY, CANCEL, COMPLETE)

    fun isKnownItemAction(value: String?): Boolean = value != null && value in ITEM_ACTIONS
}
