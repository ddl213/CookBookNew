package com.rhys.order.ui

import com.example.network.bean.OrderAction
import com.example.network.bean.OrderDto
import com.example.network.bean.OrderItemCountsDto
import com.example.network.bean.OrderItemDto
import com.example.network.bean.OrderPartyDto
import com.example.network.bean.OrderRecipeDto
import com.example.network.bean.OrderStatus
import com.rhys.order.data.OrderDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class OrderUiSupportTest {

    @Test
    fun `order status labels cover seven aggregate states`() {
        assertEquals("待接受", OrderUiSupport.orderStatusLabel(OrderStatus.PENDING_ACCEPT))
        assertEquals("备货中", OrderUiSupport.orderStatusLabel(OrderStatus.ACCEPTED))
        assertEquals("待取货", OrderUiSupport.orderStatusLabel(OrderStatus.WAITING_PICKUP))
        assertEquals("已完成", OrderUiSupport.orderStatusLabel(OrderStatus.COMPLETED))
        assertEquals("已拒绝", OrderUiSupport.orderStatusLabel(OrderStatus.REJECTED))
        assertEquals("已取消", OrderUiSupport.orderStatusLabel(OrderStatus.CANCELLED))
        assertEquals("已关闭", OrderUiSupport.orderStatusLabel(OrderStatus.CLOSED))
    }

    @Test
    fun `item status labels cover six item states`() {
        assertEquals("待接受", OrderUiSupport.itemStatusLabel(OrderStatus.PENDING_ACCEPT))
        assertEquals("备货中", OrderUiSupport.itemStatusLabel(OrderStatus.ACCEPTED))
        assertEquals("待取货", OrderUiSupport.itemStatusLabel(OrderStatus.WAITING_PICKUP))
        assertEquals("已完成", OrderUiSupport.itemStatusLabel(OrderStatus.COMPLETED))
        assertEquals("已拒绝", OrderUiSupport.itemStatusLabel(OrderStatus.REJECTED))
        assertEquals("已取消", OrderUiSupport.itemStatusLabel(OrderStatus.CANCELLED))
    }

    @Test
    fun `unknown statuses fall back safely`() {
        assertEquals("未知状态", OrderUiSupport.orderStatusLabel(null))
        assertEquals("未知状态", OrderUiSupport.orderStatusLabel(""))
        assertEquals("未知状态", OrderUiSupport.orderStatusLabel("SOMETHING_NEW"))
        assertEquals("未知状态", OrderUiSupport.itemStatusLabel("CLOSED"))
        assertEquals("未知状态", OrderUiSupport.itemStatusLabel(null))
    }

    @Test
    fun `action labels cover item actions and cancel all`() {
        assertEquals("接单", OrderUiSupport.actionLabel(OrderAction.ACCEPT))
        assertEquals("拒单", OrderUiSupport.actionLabel(OrderAction.REJECT))
        assertEquals("备货完成", OrderUiSupport.actionLabel(OrderAction.READY))
        assertEquals("取消", OrderUiSupport.actionLabel(OrderAction.CANCEL))
        assertEquals("确认取货", OrderUiSupport.actionLabel(OrderAction.COMPLETE))
        assertEquals("取消整单", OrderUiSupport.actionLabel(OrderAction.CANCEL_ALL))
    }

    @Test
    fun `unknown actions fall back safely`() {
        assertEquals("未知操作", OrderUiSupport.actionLabel(null))
        assertEquals("未知操作", OrderUiSupport.actionLabel("ARCHIVE"))
    }

    @Test
    fun `known item actions drop cancelAll and unknown values`() {
        val filtered = OrderUiSupport.knownItemActions(
            listOf(
                OrderAction.REJECT,
                OrderAction.CANCEL_ALL,
                "ARCHIVE",
                OrderAction.ACCEPT,
                OrderAction.REJECT
            )
        )

        assertEquals(listOf(OrderAction.ACCEPT, OrderAction.REJECT), filtered)
        assertTrue(OrderUiSupport.knownItemActions(null).isEmpty())
        assertTrue(OrderUiSupport.knownItemActions(emptyList()).isEmpty())
    }

    @Test
    fun `cancelAll only follows server allowed actions`() {
        assertTrue(OrderUiSupport.canCancelAll(listOf(OrderAction.CANCEL_ALL)))
        assertTrue(OrderUiSupport.canCancelAll(listOf(OrderAction.CANCEL, OrderAction.CANCEL_ALL)))
        assertFalse(OrderUiSupport.canCancelAll(listOf(OrderAction.CANCEL)))
        assertFalse(OrderUiSupport.canCancelAll(emptyList()))
        assertFalse(OrderUiSupport.canCancelAll(null))
    }

    @Test
    fun `primary action covers forward transitions only`() {
        assertTrue(OrderUiSupport.isPrimaryAction(OrderAction.ACCEPT))
        assertTrue(OrderUiSupport.isPrimaryAction(OrderAction.READY))
        assertTrue(OrderUiSupport.isPrimaryAction(OrderAction.COMPLETE))
        assertFalse(OrderUiSupport.isPrimaryAction(OrderAction.REJECT))
        assertFalse(OrderUiSupport.isPrimaryAction(OrderAction.CANCEL))
        assertFalse(OrderUiSupport.isPrimaryAction("ARCHIVE"))
    }

    @Test
    fun `count summary shows dishes and total quantity`() {
        val counts = OrderItemCountsDto(total = 2, pendingAccept = 1, accepted = 1)

        assertEquals("2 道菜 · 共 3 份", OrderUiSupport.countSummary(counts, 3L))
        assertEquals("共 3 份", OrderUiSupport.countSummary(OrderItemCountsDto(), 3L))
        assertEquals("暂无菜品", OrderUiSupport.countSummary(OrderItemCountsDto(), 0L))
        assertEquals("2 道菜 · 共 0 份", OrderUiSupport.countSummary(counts, -1L))
    }

    @Test
    fun `status breakdown lists only non zero counts`() {
        val counts = OrderItemCountsDto(
            total = 4,
            pendingAccept = 0,
            accepted = 1,
            waitingPickup = 1,
            completed = 1,
            rejected = 1,
            cancelled = 0
        )

        assertEquals(
            "备货中 1 · 待取货 1 · 已完成 1 · 已拒绝 1",
            OrderUiSupport.statusCountSummary(counts)
        )
        assertNull(OrderUiSupport.statusCountSummary(OrderItemCountsDto()))
    }

    @Test
    fun `dish summary limits names and appends more marker`() {
        val items = listOf(item(1, "番茄炒蛋"), item(2, "红烧肉"), item(3, "凉拌黄瓜"))

        assertEquals("番茄炒蛋、红烧肉 等", OrderUiSupport.dishSummary(items))
        assertEquals("番茄炒蛋", OrderUiSupport.dishSummary(items.take(1)))
        assertNull(OrderUiSupport.dishSummary(emptyList()))
        assertNull(OrderUiSupport.dishSummary(listOf(item(1, "  "))))
    }

    @Test
    fun `merge orders de-duplicates by id and keeps first position`() {
        val existing = listOf(order(1, "PENDING_ACCEPT"), order(2, "ACCEPTED"))
        val incoming = listOf(order(2, "WAITING_PICKUP"), order(3, "COMPLETED"))

        val merged = OrderUiSupport.mergeOrders(existing, incoming)

        assertEquals(listOf(1L, 2L, 3L), merged.map { it.id })
        assertEquals("WAITING_PICKUP", merged[1].status)
    }

    @Test
    fun `merge orders ignores invalid ids and supports empty pages`() {
        val merged = OrderUiSupport.mergeOrders(
            listOf(order(1, "PENDING_ACCEPT"), order(0, "ACCEPTED")),
            listOf(order(-1, "COMPLETED"))
        )

        assertEquals(listOf(1L), merged.map { it.id })
        assertTrue(OrderUiSupport.mergeOrders(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `filters keep contract order and labels`() {
        assertEquals(
            listOf(null, "PENDING_ACCEPT", "ACCEPTED", "WAITING_PICKUP", "COMPLETED",
                "REJECTED", "CANCELLED", "CLOSED"),
            OrderUiSupport.FILTERS.map { it.status }
        )
        assertEquals(
            listOf("全部", "待接受", "备货中", "待取货", "已完成", "已拒绝", "已取消", "已关闭"),
            OrderUiSupport.FILTERS.map { it.label }
        )
    }

    @Test
    fun `counterpart depends on viewer role`() {
        val order = OrderDto(
            id = 1,
            buyer = OrderPartyDto(id = 10, username = "buyer", nickname = "买家"),
            seller = OrderPartyDto(id = 20, username = "seller", nickname = "卖家")
        )

        assertEquals("卖家", OrderUiSupport.counterpart(order, OrderDataSource.ROLE_BUYER).displayName)
        assertEquals("买家", OrderUiSupport.counterpart(order, OrderDataSource.ROLE_SELLER).displayName)
        assertEquals("提供方", OrderUiSupport.counterpartLabel(OrderDataSource.ROLE_BUYER))
        assertEquals("预订方", OrderUiSupport.counterpartLabel(OrderDataSource.ROLE_SELLER))
    }

    @Test
    fun `time formatting parses utc and offsets`() {
        val shanghai = TimeZone.getTimeZone("Asia/Shanghai")

        assertEquals("2026-10-25 10:30", OrderUiSupport.formatTime("2026-10-25T02:30:00Z", shanghai))
        assertEquals(
            "2026-10-25 10:30",
            OrderUiSupport.formatTime("2026-10-25T02:30:00.500Z", shanghai)
        )
        assertEquals(
            "2026-10-25 10:30",
            OrderUiSupport.formatTime("2026-10-25T10:30:00+08:00", shanghai)
        )
        assertNull(OrderUiSupport.formatTime(null, shanghai))
        assertNull(OrderUiSupport.formatTime("", shanghai))
        assertNull(OrderUiSupport.formatTime("not-a-time", shanghai))
        assertNull(OrderUiSupport.formatTime("2026-10-25T10:30:00+99:99", shanghai))
    }

    private fun order(id: Long, status: String): OrderDto =
        OrderDto(id = id, status = status, version = 1L)

    private fun item(id: Long, name: String): OrderItemDto = OrderItemDto(
        id = id,
        recipe = OrderRecipeDto(id = id, name = name),
        quantity = 1,
        status = OrderStatus.PENDING_ACCEPT,
        version = 1L
    )
}
