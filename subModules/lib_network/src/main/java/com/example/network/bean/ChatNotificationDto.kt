package com.example.network.bean

/**
 * 消息列表中的独立预订通知项，对应 GET notifications 的 items。
 *
 * 本期契约不包含已读/未读字段；orderId 仅在预订事件关联真实订单时存在。
 * createdAt 为 ISO-8601 字符串。
 */
data class ChatNotificationDto(
    val id: Long = 0L,
    val type: String = TYPE_RESERVATION,
    val title: String = "",
    val body: String = "",
    val orderId: Long? = null,
    val createdAt: String? = null
) {
    companion object {
        const val TYPE_RESERVATION = "RESERVATION"
    }
}
