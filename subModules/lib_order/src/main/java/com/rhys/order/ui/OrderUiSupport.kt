package com.rhys.order.ui

import com.example.network.bean.OrderAction
import com.example.network.bean.OrderDto
import com.example.network.bean.OrderItemCountsDto
import com.example.network.bean.OrderItemDto
import com.example.network.bean.OrderPartyDto
import com.example.network.bean.OrderStatus
import com.rhys.order.data.OrderDataSource
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * 订单列表与详情页共用的纯逻辑。
 *
 * 只做状态/动作文案、服务端 `allowedActions` 过滤、计数摘要、列表去重与时间格式化，
 * 不访问 Android 控件、网络或 Session；未知状态/动作一律安全回退，不推断可操作。
 */
object OrderUiSupport {

    /** 列表筛选顺序：全部 + 七种整单汇总状态。 */
    val FILTERS: List<OrderFilter> = listOf(
        OrderFilter(status = null, label = "全部"),
        OrderFilter(status = OrderStatus.PENDING_ACCEPT, label = "待接受"),
        OrderFilter(status = OrderStatus.ACCEPTED, label = "备货中"),
        OrderFilter(status = OrderStatus.WAITING_PICKUP, label = "待取货"),
        OrderFilter(status = OrderStatus.COMPLETED, label = "已完成"),
        OrderFilter(status = OrderStatus.REJECTED, label = "已拒绝"),
        OrderFilter(status = OrderStatus.CANCELLED, label = "已取消"),
        OrderFilter(status = OrderStatus.CLOSED, label = "已关闭")
    )

    /** 明细动作的稳定展示/过滤顺序；`cancelAll` 不属于明细动作。 */
    val ITEM_ACTION_ORDER: List<String> = listOf(
        OrderAction.ACCEPT,
        OrderAction.REJECT,
        OrderAction.READY,
        OrderAction.CANCEL,
        OrderAction.COMPLETE
    )

    private val PRIMARY_ACTIONS = setOf(
        OrderAction.ACCEPT,
        OrderAction.READY,
        OrderAction.COMPLETE
    )

    fun orderStatusLabel(status: String?): String = when (status) {
        OrderStatus.PENDING_ACCEPT -> "待接受"
        OrderStatus.ACCEPTED -> "备货中"
        OrderStatus.WAITING_PICKUP -> "待取货"
        OrderStatus.COMPLETED -> "已完成"
        OrderStatus.REJECTED -> "已拒绝"
        OrderStatus.CANCELLED -> "已取消"
        OrderStatus.CLOSED -> "已关闭"
        else -> "未知状态"
    }

    fun itemStatusLabel(status: String?): String = when (status) {
        OrderStatus.PENDING_ACCEPT -> "待接受"
        OrderStatus.ACCEPTED -> "备货中"
        OrderStatus.WAITING_PICKUP -> "待取货"
        OrderStatus.COMPLETED -> "已完成"
        OrderStatus.REJECTED -> "已拒绝"
        OrderStatus.CANCELLED -> "已取消"
        else -> "未知状态"
    }

    fun actionLabel(action: String?): String = when (action) {
        OrderAction.ACCEPT -> "接单"
        OrderAction.REJECT -> "拒单"
        OrderAction.READY -> "备货完成"
        OrderAction.CANCEL -> "取消"
        OrderAction.COMPLETE -> "确认取货"
        OrderAction.CANCEL_ALL -> "取消整单"
        else -> "未知操作"
    }

    /**
     * 只保留服务端返回且客户端已知的明细动作。
     *
     * `cancelAll`、未知值与其他角色动作不会出现在明细按钮中；服务端仍会再次校验权限。
     */
    fun knownItemActions(allowedActions: List<String>?): List<String> {
        val allowed = allowedActions.orEmpty().toSet()
        return ITEM_ACTION_ORDER.filter { it in allowed }
    }

    /** 整单取消按钮只在服务端明确返回 `cancelAll` 时展示。 */
    fun canCancelAll(allowedActions: List<String>?): Boolean =
        allowedActions.orEmpty().contains(OrderAction.CANCEL_ALL)

    /** 正向/可继续流转的动作使用主按钮样式；拒绝与取消使用次按钮样式。 */
    fun isPrimaryAction(action: String): Boolean = action in PRIMARY_ACTIONS

    /** “2 道菜 · 共 3 份”；整单没有明细时退化为“暂无菜品”。 */
    fun countSummary(counts: OrderItemCountsDto, totalQuantity: Long): String {
        val dishes = counts.total.coerceAtLeast(0)
        val quantity = totalQuantity.coerceAtLeast(0L)
        return if (dishes > 0) {
            "$dishes 道菜 · 共 $quantity 份"
        } else if (quantity > 0L) {
            "共 $quantity 份"
        } else {
            "暂无菜品"
        }
    }

    /** 各状态明细条数，如“待接受 1 · 备货中 2”；全部为 0 时返回 null。 */
    fun statusCountSummary(counts: OrderItemCountsDto): String? {
        val parts = listOf(
            counts.pendingAccept to "待接受",
            counts.accepted to "备货中",
            counts.waitingPickup to "待取货",
            counts.completed to "已完成",
            counts.rejected to "已拒绝",
            counts.cancelled to "已取消"
        ).filter { it.first > 0 }
            .map { "${it.second} ${it.first}" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** 列表卡片菜品摘要：最多展示两道菜名，更多时追加“等”。 */
    fun dishSummary(items: List<OrderItemDto>): String? {
        val names = items.map { it.recipe.name.trim() }
            .filter { it.isNotEmpty() }
        if (names.isEmpty()) return null
        val shown = names.take(2).joinToString("、")
        return if (names.size > 2) "$shown 等" else shown
    }

    /** 买家视角看提供方，卖家视角看预订方。 */
    fun counterpart(order: OrderDto, role: String): OrderPartyDto =
        if (role == OrderDataSource.ROLE_SELLER) order.buyer else order.seller

    /** 对方角色说明，用于列表卡片副标题。 */
    fun counterpartLabel(role: String): String =
        if (role == OrderDataSource.ROLE_SELLER) "预订方" else "提供方"

    /**
     * 分页追加/刷新时按订单 ID 去重；后到的同 ID 数据覆盖旧值但保持首次出现的位置。
     */
    fun mergeOrders(existing: List<OrderDto>, incoming: List<OrderDto>): List<OrderDto> {
        val merged = LinkedHashMap<Long, OrderDto>()
        existing.forEach { order ->
            if (order.id > 0L) merged[order.id] = order
        }
        incoming.forEach { order ->
            if (order.id > 0L) merged[order.id] = order
        }
        return merged.values.toList()
    }

    /**
     * 将服务端 UTC ISO-8601 时间格式化为本地 `yyyy-MM-dd HH:mm`。
     *
     * 支持 `Z`、`±HH:MM`/`±HHMM` 与可选毫秒；无法解析时返回 null，页面不展示时间。
     * [zone] 仅用于输出，测试可固定时区。
     */
    fun formatTime(raw: String?, zone: TimeZone = TimeZone.getDefault()): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        var body = value
        var inputZone = TimeZone.getTimeZone("UTC")
        if (body.endsWith("Z", ignoreCase = true)) {
            body = body.dropLast(1)
        } else {
            val signIndex = body.lastIndexOf('+').takeIf { it > 10 }
                ?: body.lastIndexOf('-').takeIf { it > 10 }
            if (signIndex != null) {
                val offset = body.substring(signIndex)
                body = body.substring(0, signIndex)
                inputZone = parseOffset(offset) ?: return null
            }
        }

        val date = DATE_PATTERNS.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = inputZone
                }.parse(body)
            }.getOrNull()
        } ?: return null

        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
            timeZone = zone
        }.format(date)
    }

    private fun parseOffset(offset: String): TimeZone? {
        if (!OFFSET_PATTERN.matches(offset)) return null
        val hours = offset.substring(1, 3).toIntOrNull() ?: return null
        val minutes = offset.substring(offset.length - 2).toIntOrNull() ?: return null
        if (hours > 23 || minutes > 59) return null
        val sign = if (offset[0] == '-') "-" else "+"
        val normalized = sign + hours.toString().padStart(2, '0') +
            minutes.toString().padStart(2, '0')
        return TimeZone.getTimeZone("GMT$normalized")
    }

    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss"
    )

    private val OFFSET_PATTERN = Regex("^[+-]\\d{2}:?\\d{2}$")
}

/** 列表筛选选项；`status=null` 表示全部。 */
data class OrderFilter(
    val status: String?,
    val label: String
)
