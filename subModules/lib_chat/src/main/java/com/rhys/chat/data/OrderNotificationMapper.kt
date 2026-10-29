package com.rhys.chat.data

import com.example.network.bean.OrderNotificationActorDto
import com.example.network.bean.OrderNotificationDto
import com.example.network.bean.OrderNotificationItemDto
import com.example.network.bean.OrderNotificationPayloadDto
import com.example.network.order.OrderNotificationEvent
import com.example.network.order.OrderNotificationType
import com.rhys.chat.domain.ChatNotification

/**
 * 订单通知 DTO / Mock 事件 → 聊天模块现有 [ChatNotification] 的纯映射。
 *
 * 规则：
 * - 顶层 `title/body/orderId` 与 payload 内旧字段优先，保证 P07 通知继续可用；
 * - 没有显式文案时按六类订单事件生成中文，未知 type 使用通用“预订通知”；
 * - `recipe_share` 生成“菜谱分享 / 「分享者」分享了「菜名」”并携带菜谱 ID，不伪造订单 ID；
 * - `read/readAt` 不参与映射，不生成未读/已读状态；
 * - `id<=0`、payload 或多数字段缺失时安全返回 null/兜底，不抛异常。
 */
internal object OrderNotificationMapper {

    private const val ACTOR_FALLBACK = "好友"
    private const val ITEM_FALLBACK = "你的菜品"
    private const val MAX_ITEMS = 2
    private const val TYPE_RECIPE_SHARE = "recipe_share"
    private const val RECIPE_SHARE_TITLE = "菜谱分享"
    private const val RECIPE_NAME_FALLBACK = "菜谱"

    fun map(dto: OrderNotificationDto): ChatNotification? {
        if (dto.id <= 0L) return null
        val payload = dto.payload
        val type = trimOrEmpty(dto.type)
        val createdAtRaw = dto.createdAt?.takeIf { it.isNotBlank() }
        if (type.equals(TYPE_RECIPE_SHARE, ignoreCase = true)) {
            return mapRecipeShare(dto, payload, type, createdAtRaw)
        }
        val orderId = positive(dto.orderId) ?: positive(payload?.orderId)
        val generated = generatedCopy(
            type = type,
            actor = actorName(payload),
            items = itemSummary(payload)
        )
        val title = trimOrEmpty(dto.title)
            .ifEmpty { trimOrEmpty(payload?.title) }
            .ifEmpty { generated.first }
        val body = trimOrEmpty(dto.body)
            .ifEmpty { trimOrEmpty(payload?.body) }
            .ifEmpty { generated.second }
        return ChatNotification(
            id = dto.id,
            type = type,
            title = title,
            body = body,
            orderId = orderId,
            createdAt = ChatTime.parseMillis(createdAtRaw),
            createdAtRaw = createdAtRaw
        )
    }

    /**
     * `recipe_share` 通知：标题“菜谱分享”，正文“「分享者」分享了「菜名」”，
     * 只携带菜谱 ID，不伪造订单关联；显式 title/body 仍优先。
     */
    private fun mapRecipeShare(
        dto: OrderNotificationDto,
        payload: OrderNotificationPayloadDto?,
        type: String,
        createdAtRaw: String?
    ): ChatNotification {
        val sharer = shareUserName(payload)
        val recipeName = trimOrEmpty(payload?.recipeName).ifEmpty { RECIPE_NAME_FALLBACK }
        val title = trimOrEmpty(dto.title)
            .ifEmpty { trimOrEmpty(payload?.title) }
            .ifEmpty { RECIPE_SHARE_TITLE }
        val body = trimOrEmpty(dto.body)
            .ifEmpty { trimOrEmpty(payload?.body) }
            .ifEmpty { "「$sharer」分享了「$recipeName」" }
        return ChatNotification(
            id = dto.id,
            type = type,
            title = title,
            body = body,
            orderId = null,
            recipeId = positive(payload?.recipeId),
            createdAt = ChatTime.parseMillis(createdAtRaw),
            createdAtRaw = createdAtRaw
        )
    }

    /** Mock 事件与远端 DTO 使用同一套文案生成；事件 ID 由存储分配。 */
    fun map(event: OrderNotificationEvent): ChatNotification? = map(
        OrderNotificationDto(
            id = event.id,
            type = event.type,
            payload = OrderNotificationPayloadDto(
                schemaVersion = event.schemaVersion,
                orderId = event.orderId,
                orderStatus = event.orderStatus,
                actor = OrderNotificationActorDto(
                    id = event.actor.id,
                    nickname = event.actor.nickname
                ),
                items = event.items.map { item ->
                    OrderNotificationItemDto(
                        itemId = item.itemId,
                        recipeId = item.recipeId,
                        recipeName = item.recipeName,
                        quantity = item.quantity,
                        fromStatus = item.fromStatus,
                        toStatus = item.toStatus
                    )
                }
            ),
            createdAt = event.createdAt
        )
    )

    private fun generatedCopy(type: String, actor: String, items: String): Pair<String, String> =
        when (type) {
            OrderNotificationType.ORDER_CREATED ->
                "新的预订请求" to "「$actor」预订了「$items」"

            OrderNotificationType.ORDER_ACCEPTED ->
                "预订已接受" to "「$items」已接单，正在备货"

            OrderNotificationType.ORDER_REJECTED ->
                "预订被拒绝" to "「$items」已被拒绝"

            OrderNotificationType.ORDER_READY ->
                "菜品已备好" to "「$items」已备好，请前往取货"

            OrderNotificationType.ORDER_CANCELLED ->
                "预订已取消" to "「$items」已取消"

            OrderNotificationType.ORDER_COMPLETED ->
                "预订已完成" to "「$items」已确认取货"

            else ->
                "预订通知" to "订单状态有更新"
        }

    private fun actorName(payload: OrderNotificationPayloadDto?): String {
        val actorNickname = trimOrEmpty(payload?.actor?.nickname)
        if (actorNickname.isNotEmpty()) return actorNickname
        val buyerName = trimOrEmpty(payload?.buyer?.nickname)
            .ifEmpty { trimOrEmpty(payload?.buyer?.username) }
        return buyerName.ifEmpty { ACTOR_FALLBACK }
    }

    /**
     * 明细摘要：“菜名×数量”，最多两道，更多追加“等”。
     * 新 payload 无明细时回退旧单菜品 `recipeName`；仍为空则使用安全占位。
     */
    private fun itemSummary(payload: OrderNotificationPayloadDto?): String {
        val items = payload?.items.orEmpty().filterNotNull().mapNotNull { item ->
            val name = trimOrEmpty(item.recipeName)
            when {
                name.isEmpty() -> null
                item.quantity > 0 -> "$name×${item.quantity}"
                else -> name
            }
        }
        if (items.isNotEmpty()) {
            val shown = items.take(MAX_ITEMS).joinToString("、")
            return if (items.size > MAX_ITEMS) "${shown}等" else shown
        }
        return trimOrEmpty(payload?.recipeName).ifEmpty { ITEM_FALLBACK }
    }

    /** 分享者昵称优先，其次用户名，缺失时使用安全占位。 */
    private fun shareUserName(payload: OrderNotificationPayloadDto?): String {
        val nickname = trimOrEmpty(payload?.fromUser?.nickname)
        if (nickname.isNotEmpty()) return nickname
        return trimOrEmpty(payload?.fromUser?.username).ifEmpty { ACTOR_FALLBACK }
    }

    private fun positive(value: Long?): Long? = value?.takeIf { it > 0L }

    /**
     * Gson 对缺省字段可能保留 JVM 默认 null；这里统一按可空读取，避免字段缺失崩溃。
     */
    private fun trimOrEmpty(value: String?): String = value?.trim().orEmpty()
}
