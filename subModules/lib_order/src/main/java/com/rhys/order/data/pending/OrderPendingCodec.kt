package com.rhys.order.data.pending

import com.example.network.bean.CreateOrderItem
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 待确认请求的明文编码。
 *
 * 该文本在落盘前必须整体加密；使用 URL 编码处理任意备注字符，
 * 避免依赖 JSON 库或分隔符转义缺陷。
 */
internal object OrderPendingCodec {

    private const val HEADER = "ORDER_PENDING_V1"
    private const val LINE_SEPARATOR = "\n"
    private const val ITEM_SEPARATOR = ","

    fun encode(pending: PendingOrderCreate): String = buildString {
        append(HEADER).append(LINE_SEPARATOR)
        append(pending.createdAt).append(LINE_SEPARATOR)
        append(URLEncoder.encode(pending.note.orEmpty(), Charsets.UTF_8.name()))
        pending.items.forEach { item ->
            append(LINE_SEPARATOR)
            append(item.recipeId).append(ITEM_SEPARATOR).append(item.quantity)
        }
    }

    /** 解码失败返回 null，由仓储视为不可读 pending，不猜内容。 */
    fun decode(plaintext: String): PendingOrderCreate? {
        val lines = plaintext.split(LINE_SEPARATOR)
        if (lines.size < 3 || lines[0] != HEADER) return null
        val createdAt = lines[1].toLongOrNull() ?: return null
        val note = try {
            URLDecoder.decode(lines[2], Charsets.UTF_8.name())
                .trim()
                .takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            return null
        }
        val items = mutableListOf<CreateOrderItem>()
        for (index in 3 until lines.size) {
            val line = lines[index]
            if (line.isEmpty()) return null
            val parts = line.split(ITEM_SEPARATOR)
            if (parts.size != 2) return null
            val recipeId = parts[0].toLongOrNull() ?: return null
            val quantity = parts[1].toIntOrNull() ?: return null
            if (recipeId <= 0L || quantity <= 0) return null
            items += CreateOrderItem(recipeId = recipeId, quantity = quantity)
        }
        if (items.isEmpty()) return null
        return PendingOrderCreate(
            idempotencyKey = "",
            items = items,
            note = note,
            createdAt = createdAt,
            readable = true
        )
    }
}
