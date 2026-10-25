package com.example.network.bean

/**
 * GET notifications?category=order 返回的订单通知项，对齐 P08 订单契约 §5。
 *
 * 新后端返回 `id/type/payload/read/readAt/createdAt`；顶层 `title/body/orderId`
 * 是 P07 旧通知的兼容回退字段。`read/readAt` 仅随响应传输，本期不展示、不推断未读/已读。
 */
data class OrderNotificationDto(
    val id: Long = 0L,
    val type: String = "",
    val payload: OrderNotificationPayloadDto? = null,
    val read: Boolean = false,
    val readAt: String? = null,
    val createdAt: String? = null,
    val title: String? = null,
    val body: String? = null,
    val orderId: Long? = null
)

/**
 * 订单通知 payload（schemaVersion 2）。
 *
 * 同时兼容 P07 单菜品订阅通知：旧字段 `recipeId/recipeName/status/buyer` 可单独存在，
 * 缺少 `actor/items` 时由客户端按旧结构生成展示文案。
 */
data class OrderNotificationPayloadDto(
    val schemaVersion: Int? = null,
    val orderId: Long? = null,
    val orderStatus: String? = null,
    val actor: OrderNotificationActorDto? = null,
    val items: List<OrderNotificationItemDto> = emptyList(),
    // P07 旧 payload（单菜品）
    val recipeId: Long? = null,
    val recipeName: String? = null,
    val status: String? = null,
    val buyer: OrderNotificationBuyerDto? = null,
    // 部分旧通知可能把标题/正文放在 payload 内
    val title: String? = null,
    val body: String? = null
)

/** 触发通知的操作者快照。 */
data class OrderNotificationActorDto(
    val id: Long = 0L,
    val nickname: String = ""
)

/** 本次通知涉及的订单明细快照；`recipeId` 可在菜谱删除后为空。 */
data class OrderNotificationItemDto(
    val itemId: Long = 0L,
    val recipeId: Long? = null,
    val recipeName: String = "",
    val quantity: Int = 0,
    val fromStatus: String? = null,
    val toStatus: String = ""
)

/** P07 旧 payload 中的预订方；用于没有 actor 时生成“谁预订了”的文案。 */
data class OrderNotificationBuyerDto(
    val id: Long = 0L,
    val username: String = "",
    val nickname: String = ""
) {
    val displayName: String
        get() = nickname.ifBlank { username }
}
