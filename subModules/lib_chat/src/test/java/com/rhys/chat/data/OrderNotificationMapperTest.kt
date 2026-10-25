package com.rhys.chat.data

import com.example.network.bean.OrderNotificationActorDto
import com.example.network.bean.OrderNotificationBuyerDto
import com.example.network.bean.OrderNotificationDto
import com.example.network.bean.OrderNotificationItemDto
import com.example.network.bean.OrderNotificationPayloadDto
import com.example.network.order.OrderNotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 订单通知到 `ChatNotification` 的纯映射测试。
 *
 * 覆盖六类文案、旧 title/body、旧单菜品 payload、未知 type、payload 缺失、
 * 非法 id、orderId/时间优先级与明细截断；不访问网络或数据库。
 */
class OrderNotificationMapperTest {

    @Test
    fun `maps six order types to Chinese copy`() {
        val cases = listOf(
            OrderNotificationType.ORDER_CREATED to
                ("新的预订请求" to "「小林」预订了「红烧肉×2」"),
            OrderNotificationType.ORDER_ACCEPTED to
                ("预订已接受" to "「红烧肉×2」已接单，正在备货"),
            OrderNotificationType.ORDER_REJECTED to
                ("预订被拒绝" to "「红烧肉×2」已被拒绝"),
            OrderNotificationType.ORDER_READY to
                ("菜品已备好" to "「红烧肉×2」已备好，请前往取货"),
            OrderNotificationType.ORDER_CANCELLED to
                ("预订已取消" to "「红烧肉×2」已取消"),
            OrderNotificationType.ORDER_COMPLETED to
                ("预订已完成" to "「红烧肉×2」已确认取货")
        )
        cases.forEach { (type, expected) ->
            val mapped = OrderNotificationMapper.map(dto(type = type))
            requireNotNull(mapped) { "type=$type should map" }
            assertEquals(expected.first, mapped.title)
            assertEquals(expected.second, mapped.body)
        }
    }

    @Test
    fun `explicit title and body win over generated copy`() {
        val mapped = OrderNotificationMapper.map(
            dto(
                type = OrderNotificationType.ORDER_ACCEPTED,
                title = "旧标题",
                body = "旧正文"
            )
        )
        requireNotNull(mapped)
        assertEquals("旧标题", mapped.title)
        assertEquals("旧正文", mapped.body)
    }

    @Test
    fun `legacy single dish payload uses recipeName and buyer nickname`() {
        val mapped = OrderNotificationMapper.map(
            OrderNotificationDto(
                id = 7L,
                type = OrderNotificationType.ORDER_CREATED,
                payload = OrderNotificationPayloadDto(
                    orderId = 900L,
                    recipeId = 11L,
                    recipeName = "番茄炒蛋",
                    status = "PENDING_ACCEPT",
                    buyer = OrderNotificationBuyerDto(
                        id = 2L,
                        username = "lin_xiaochu",
                        nickname = "小林"
                    )
                )
            )
        )
        requireNotNull(mapped)
        assertEquals("新的预订请求", mapped.title)
        assertEquals("「小林」预订了「番茄炒蛋」", mapped.body)
        assertEquals(900L, mapped.orderId)
    }

    @Test
    fun `legacy buyer falls back to username when nickname is blank`() {
        val mapped = OrderNotificationMapper.map(
            OrderNotificationDto(
                id = 8L,
                type = OrderNotificationType.ORDER_CREATED,
                payload = OrderNotificationPayloadDto(
                    recipeName = "番茄炒蛋",
                    buyer = OrderNotificationBuyerDto(
                        id = 2L,
                        username = "lin_xiaochu",
                        nickname = ""
                    )
                )
            )
        )
        requireNotNull(mapped)
        assertEquals("「lin_xiaochu」预订了「番茄炒蛋」", mapped.body)
    }

    @Test
    fun `unknown type uses generic copy`() {
        val mapped = OrderNotificationMapper.map(dto(type = "RESERVATION"))
        requireNotNull(mapped)
        assertEquals("预订通知", mapped.title)
        assertEquals("订单状态有更新", mapped.body)
        assertEquals("RESERVATION", mapped.type)
    }

    @Test
    fun `missing payload does not crash and uses safe fallbacks`() {
        val mapped = OrderNotificationMapper.map(
            OrderNotificationDto(
                id = 3L,
                type = OrderNotificationType.ORDER_READY,
                payload = null,
                createdAt = null
            )
        )
        requireNotNull(mapped)
        assertEquals("菜品已备好", mapped.title)
        assertEquals("「你的菜品」已备好，请前往取货", mapped.body)
        assertNull(mapped.orderId)
        assertEquals(0L, mapped.createdAt)
        assertNull(mapped.createdAtRaw)
    }

    @Test
    fun `invalid id returns null`() {
        assertNull(OrderNotificationMapper.map(dto(id = 0L)))
        assertNull(OrderNotificationMapper.map(dto(id = -3L)))
    }

    @Test
    fun `top level order id wins and time preserves raw value`() {
        val mapped = OrderNotificationMapper.map(
            OrderNotificationDto(
                id = 4L,
                type = OrderNotificationType.ORDER_ACCEPTED,
                orderId = 123L,
                payload = OrderNotificationPayloadDto(
                    orderId = 456L,
                    actor = OrderNotificationActorDto(id = 9L, nickname = "小林"),
                    items = listOf(item("红烧肉", 2))
                ),
                createdAt = "2026-10-20T09:10:00Z"
            )
        )
        requireNotNull(mapped)
        assertEquals(123L, mapped.orderId)
        assertTrue(mapped.createdAt > 0L)
        assertEquals("2026-10-20T09:10:00Z", mapped.createdAtRaw)
    }

    @Test
    fun `payload order id is used when top level is missing`() {
        val mapped = OrderNotificationMapper.map(
            OrderNotificationDto(
                id = 5L,
                type = OrderNotificationType.ORDER_READY,
                orderId = null,
                payload = OrderNotificationPayloadDto(
                    orderId = 456L,
                    actor = OrderNotificationActorDto(id = 9L, nickname = "小林"),
                    items = listOf(item("红烧肉", 2))
                )
            )
        )
        requireNotNull(mapped)
        assertEquals(456L, mapped.orderId)
    }

    @Test
    fun `items are truncated to two with suffix`() {
        val mapped = OrderNotificationMapper.map(
            dto(
                type = OrderNotificationType.ORDER_ACCEPTED,
                items = listOf(
                    item("A", 1),
                    item("B", 2),
                    item("C", 3)
                )
            )
        )
        requireNotNull(mapped)
        assertEquals("「A×1、B×2等」已接单，正在备货", mapped.body)
    }

    private fun dto(
        id: Long = 1L,
        type: String = OrderNotificationType.ORDER_CREATED,
        orderId: Long = 500L,
        items: List<OrderNotificationItemDto> = listOf(item("红烧肉", 2)),
        createdAt: String? = "2026-10-20T09:10:00Z",
        title: String? = null,
        body: String? = null
    ): OrderNotificationDto = OrderNotificationDto(
        id = id,
        type = type,
        payload = OrderNotificationPayloadDto(
            schemaVersion = 2,
            orderId = orderId,
            orderStatus = "PENDING_ACCEPT",
            actor = OrderNotificationActorDto(id = 9L, nickname = "小林"),
            items = items
        ),
        createdAt = createdAt,
        title = title,
        body = body
    )

    private fun item(name: String, quantity: Int): OrderNotificationItemDto =
        OrderNotificationItemDto(
            itemId = 1L,
            recipeId = 1L,
            recipeName = name,
            quantity = quantity,
            fromStatus = null,
            toStatus = "PENDING_ACCEPT"
        )
}
