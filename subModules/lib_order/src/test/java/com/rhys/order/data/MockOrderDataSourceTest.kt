package com.rhys.order.data

import com.example.network.AuthException
import com.example.network.bean.CreateOrderItem
import com.example.network.bean.OrderAction
import com.example.network.bean.OrderDto
import com.example.network.bean.OrderStatus
import com.example.network.order.InMemoryOrderNotificationStore
import com.example.network.order.OrderNotificationType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Mock 订单数据源的六态、汇总、幂等、版本、取消、通知与双账号共享。 */
class MockOrderDataSourceTest {

    private val buyer = OrderMockActor(id = 1L, username = "buyer", nickname = "买方")
    private val seller = OrderMockActor(id = 2L, username = "seller", nickname = "卖方")
    private val thirdParty = OrderMockActor(id = 3L, username = "third", nickname = "第三方")

    private var currentActor: OrderMockActor? = buyer

    private val store = MockOrderStore()
    private val notifications = InMemoryOrderNotificationStore()
    private val source = MockOrderDataSource(
        store = store,
        actorProvider = OrderActorProvider { currentActor },
        notificationStore = notifications,
        now = { "2026-10-25T12:00:00Z" }
    )

    @Test
    fun `mock is marked and first order id is deterministic`() = runBlocking {
        assertTrue(source.isMock)
        val order = create(listOf(101 to 1))
        assertEquals(MockOrderDataSource.FIRST_ORDER_ID, order.id)
        assertEquals("2026-10-25T12:00:00Z", order.createdAt)
        assertEquals(1L, order.version)
        assertEquals(1L, order.items.single().version)
    }

    @Test
    fun `create merges duplicate recipes sorts and sums counts`() = runBlocking {
        val order = create(
            listOf(102 to 1, 101 to 2, 101 to 1)
        )

        assertEquals(2, order.items.size)
        assertEquals(101L, order.items[0].recipe.id)
        assertEquals(3, order.items[0].quantity)
        assertEquals(102L, order.items[1].recipe.id)
        assertEquals(4L, order.totalQuantity)
        assertEquals(2, order.itemCounts.total)
        assertEquals(2, order.itemCounts.pendingAccept)
        assertEquals(OrderStatus.PENDING_ACCEPT, order.status)
        assertEquals(listOf(OrderAction.CANCEL_ALL), order.allowedActions)
    }

    @Test
    fun `buyer and seller allowed actions follow item state`() = runBlocking {
        val order = create(listOf(101 to 1))
        val itemId = order.items.single().id
        assertEquals(listOf(OrderAction.CANCEL), order.items.single().allowedActions)

        switchTo(seller)
        val sellerView = source.getOrder(order.id)
        assertTrue(sellerView.allowedActions.isEmpty())
        assertEquals(
            listOf(OrderAction.ACCEPT, OrderAction.REJECT),
            sellerView.items.single().allowedActions
        )

        val accepted = source.actOnItem(order.id, itemId, OrderAction.ACCEPT, 1L)
        assertEquals(OrderStatus.ACCEPTED, accepted.status)
        assertEquals(listOf(OrderAction.READY), accepted.items.single().allowedActions)

        val ready = source.actOnItem(order.id, itemId, OrderAction.READY, 2L)
        assertEquals(OrderStatus.WAITING_PICKUP, ready.status)
        assertTrue(ready.items.single().allowedActions.isEmpty())

        switchTo(buyer)
        val buyerView = source.getOrder(order.id)
        assertEquals(listOf(OrderAction.COMPLETE), buyerView.items.single().allowedActions)
    }

    @Test
    fun `full item action chain reaches completed with versions`() = runBlocking {
        val order = create(listOf(101 to 2))
        val itemId = order.items.single().id

        switchTo(seller)
        val accepted = source.actOnItem(order.id, itemId, OrderAction.ACCEPT, 1L)
        assertEquals(OrderStatus.ACCEPTED, accepted.items.single().status)
        assertEquals(2L, accepted.items.single().version)
        assertEquals(2L, accepted.version)

        val ready = source.actOnItem(order.id, itemId, OrderAction.READY, 2L)
        assertEquals(OrderStatus.WAITING_PICKUP, ready.items.single().status)
        assertEquals(3L, ready.items.single().version)
        assertEquals(3L, ready.version)

        switchTo(buyer)
        val completed = source.actOnItem(order.id, itemId, OrderAction.COMPLETE, 3L)
        assertEquals(OrderStatus.COMPLETED, completed.status)
        assertEquals(OrderStatus.COMPLETED, completed.items.single().status)
        assertEquals(4L, completed.version)
        assertEquals(4L, completed.items.single().version)
        assertTrue(completed.items.single().allowedActions.isEmpty())
        assertTrue(completed.allowedActions.isEmpty())
    }

    @Test
    fun `reject and cancel are terminal and mixed summary is closed`() = runBlocking {
        val order = create(listOf(101 to 1, 102 to 1))
        val rejectedItem = order.items[0]
        val cancelledItem = order.items[1]

        switchTo(seller)
        val rejected = source.actOnItem(order.id, rejectedItem.id, OrderAction.REJECT, 1L)
        assertEquals(OrderStatus.REJECTED, rejected.items[0].status)
        assertTrue(rejected.items[0].allowedActions.isEmpty())

        switchTo(buyer)
        val cancelled = source.actOnItem(order.id, cancelledItem.id, OrderAction.CANCEL, 1L)
        assertEquals(OrderStatus.CANCELLED, cancelled.items[1].status)
        assertEquals(OrderStatus.CLOSED, cancelled.status)
        assertEquals(1, cancelled.itemCounts.rejected)
        assertEquals(1, cancelled.itemCounts.cancelled)
        assertTrue(cancelled.allowedActions.isEmpty())
        assertTrue(cancelled.items[0].allowedActions.isEmpty())
        assertTrue(cancelled.items[1].allowedActions.isEmpty())
    }

    @Test
    fun `whole cancel requires all pending and cancels atomically`() = runBlocking {
        val order = create(listOf(101 to 2, 102 to 1))

        val cancelled = source.cancelOrder(order.id, order.version)

        assertEquals(OrderStatus.CANCELLED, cancelled.status)
        assertTrue(cancelled.items.all { it.status == OrderStatus.CANCELLED })
        assertEquals(2L, cancelled.version)
        assertTrue(cancelled.items.all { it.version == 2L })
        assertEquals(2, cancelled.itemCounts.cancelled)
        assertEquals(3L, cancelled.totalQuantity)
        val events = notifications.list(seller.id)
            .filter { it.type == OrderNotificationType.ORDER_CANCELLED && it.orderId == order.id }
        assertEquals(1, events.size)
        assertEquals(2, events.single().items.size)
    }

    @Test
    fun `whole cancel rejects when any item is not pending`() = runBlocking {
        val order = create(listOf(101 to 1, 102 to 1))
        switchTo(seller)
        source.actOnItem(order.id, order.items[0].id, OrderAction.ACCEPT, 1L)
        switchTo(buyer)

        expectAuth("ORDER_NOT_CANCELLABLE") {
            source.cancelOrder(order.id, expectedVersion = 2L)
        }
    }

    @Test
    fun `version conflict keeps state unchanged`() = runBlocking {
        val order = create(listOf(101 to 1))
        switchTo(seller)

        expectAuth("ORDER_VERSION_CONFLICT") {
            source.actOnItem(order.id, order.items.single().id, OrderAction.ACCEPT, 2L)
        }

        val current = source.getOrder(order.id)
        assertEquals(OrderStatus.PENDING_ACCEPT, current.items.single().status)
        assertEquals(1L, current.items.single().version)
        assertEquals(1L, current.version)
    }

    @Test
    fun `partial accept blocks whole cancel and shows per item actions`() = runBlocking {
        val order = create(listOf(101 to 1, 102 to 1))
        switchTo(seller)
        val partial = source.actOnItem(order.id, order.items[0].id, OrderAction.ACCEPT, 1L)

        assertEquals(OrderStatus.PENDING_ACCEPT, partial.status)
        assertEquals(listOf(OrderAction.READY), partial.items[0].allowedActions)
        assertEquals(
            listOf(OrderAction.ACCEPT, OrderAction.REJECT),
            partial.items[1].allowedActions
        )
        assertTrue(partial.allowedActions.isEmpty())

        switchTo(buyer)
        val buyerView = source.getOrder(order.id)
        assertTrue(buyerView.allowedActions.isEmpty())
        assertEquals(listOf(OrderAction.CANCEL), buyerView.items[1].allowedActions)
    }

    @Test
    fun `same key same content replays and different content conflicts`() = runBlocking {
        val first = create(listOf(101 to 2, 102 to 1), key = "same-key")
        val replay = create(
            listOf(102 to 1, 101 to 2),
            key = "same-key",
            note = null
        )

        assertEquals(first.id, replay.id)
        assertEquals(1, store.orders.size)

        expectAuth("IDEMPOTENCY_KEY_REUSED") {
            create(listOf(101 to 2), key = "same-key")
        }
        assertEquals(1, store.orders.size)
    }

    @Test
    fun `create rejects non friend own recipe mixed sellers and invalid input`() = runBlocking {
        expectAuth("NOT_FRIENDS") {
            create(listOf(101 to 1), context = demoContext().copy(isFriend = false))
        }
        expectAuth("CANNOT_ORDER_OWN_RECIPE") {
            create(
                listOf(101 to 1),
                context = demoContext().copy(
                    sellerId = buyer.id,
                    sellerName = buyer.nickname,
                    recipes = mapOf(
                        101L to OrderDemoRecipe(
                            name = "自己发布的菜",
                            sellerId = buyer.id
                        )
                    )
                )
            )
        }
        expectAuth("MIXED_SELLERS") {
            create(
                listOf(101 to 1, 201 to 1),
                context = demoContext().copy(
                    recipes = demoContext().recipes + (
                        201L to OrderDemoRecipe(
                            name = "另一家的菜",
                            sellerId = thirdParty.id,
                            sellerName = thirdParty.nickname
                        )
                        )
                )
            )
        }
        expectAuth("RECIPE_NOT_FOUND") {
            create(listOf(999 to 1))
        }
        expectAuth("VALIDATION_ERROR") {
            create(listOf(101 to 0))
        }
        expectAuth("VALIDATION_ERROR") {
            create(listOf(101 to Int.MAX_VALUE, 101 to 1))
        }
        expectAuth("VALIDATION_ERROR") {
            create(emptyList())
        }
    }

    @Test
    fun `two accounts in one process see the same order and third party gets 404`() = runBlocking {
        val order = create(listOf(101 to 1))

        switchTo(seller)
        val sellerPage = source.listOrders(role = OrderDataSource.ROLE_SELLER)
        assertEquals(1, sellerPage.total)
        assertEquals(order.id, sellerPage.items.single().id)
        assertEquals(
            listOf(OrderAction.ACCEPT, OrderAction.REJECT),
            sellerPage.items.single().items.single().allowedActions
        )

        switchTo(buyer)
        val buyerPage = source.listOrders(role = OrderDataSource.ROLE_BUYER)
        assertEquals(1, buyerPage.total)
        assertEquals(order.id, buyerPage.items.single().id)

        switchTo(thirdParty)
        // 第三方访问与不存在同样返回 404，不泄露订单是否存在。
        expectAuth("ORDER_NOT_FOUND") { source.getOrder(order.id) }
        expectAuth("ORDER_NOT_FOUND") {
            source.actOnItem(order.id, order.items.single().id, OrderAction.ACCEPT, 1L)
        }
    }

    @Test
    fun `separate mock instances sharing a store see the same order`() = runBlocking {
        val sharedStore = MockOrderStore()
        val sharedNotifications = InMemoryOrderNotificationStore()
        val buyerSource = MockOrderDataSource(
            store = sharedStore,
            actorProvider = OrderActorProvider { buyer },
            notificationStore = sharedNotifications,
            now = { "2026-10-25T12:00:00Z" }
        )
        val sellerSource = MockOrderDataSource(
            store = sharedStore,
            actorProvider = OrderActorProvider { seller },
            notificationStore = sharedNotifications,
            now = { "2026-10-25T12:00:00Z" }
        )

        val order = buyerSource.createOrder(
            items = listOf(CreateOrderItem(101L, 1)),
            note = null,
            idempotencyKey = "shared-key",
            demoContext = demoContext()
        )

        val sellerPage = sellerSource.listOrders(role = OrderDataSource.ROLE_SELLER)
        assertEquals(1, sellerPage.total)
        assertEquals(order.id, sellerPage.items.single().id)
        assertEquals(1, buyerSource.listOrders(role = OrderDataSource.ROLE_BUYER).total)
    }

    @Test
    fun `six notification types carry recipient actor and items`() = runBlocking {
        val created = create(listOf(101 to 1), key = "n1")
        val createdEvent = notifications.list(seller.id).single()
        assertEquals(OrderNotificationType.ORDER_CREATED, createdEvent.type)
        assertEquals(buyer.id, createdEvent.actor.id)
        assertEquals(created.id, createdEvent.orderId)
        assertEquals(OrderStatus.PENDING_ACCEPT, createdEvent.orderStatus)

        switchTo(seller)
        source.actOnItem(created.id, created.items.single().id, OrderAction.ACCEPT, 1L)
        val acceptedEvent = notifications.list(buyer.id).first {
            it.type == OrderNotificationType.ORDER_ACCEPTED
        }
        assertEquals(seller.id, acceptedEvent.actor.id)
        assertEquals(OrderNotificationType.ORDER_ACCEPTED, acceptedEvent.type)

        source.actOnItem(created.id, created.items.single().id, OrderAction.READY, 2L)
        assertTrue(
            notifications.list(buyer.id).any { it.type == OrderNotificationType.ORDER_READY }
        )

        switchTo(buyer)
        source.actOnItem(created.id, created.items.single().id, OrderAction.COMPLETE, 3L)
        assertTrue(
            notifications.list(seller.id).any {
                it.type == OrderNotificationType.ORDER_COMPLETED
            }
        )

        val rejectedOrder = create(listOf(102 to 1), key = "n2")
        switchTo(seller)
        source.actOnItem(rejectedOrder.id, rejectedOrder.items.single().id, OrderAction.REJECT, 1L)
        assertTrue(
            notifications.list(buyer.id).any { it.type == OrderNotificationType.ORDER_REJECTED }
        )

        switchTo(buyer)
        val cancelledOrder = create(listOf(101 to 1), key = "n3")
        source.actOnItem(
            cancelledOrder.id,
            cancelledOrder.items.single().id,
            OrderAction.CANCEL,
            1L
        )
        assertTrue(
            notifications.list(seller.id).any { it.type == OrderNotificationType.ORDER_CANCELLED }
        )
    }

    @Test
    fun `pagination sorts by id and status filter uses summary`() = runBlocking {
        create(listOf(101 to 1), key = "p1")
        create(listOf(101 to 1), key = "p2")
        create(listOf(101 to 1), key = "p3")
        val acceptedOrder = create(listOf(101 to 1), key = "p4")
        switchTo(seller)
        source.actOnItem(acceptedOrder.id, acceptedOrder.items.single().id, OrderAction.ACCEPT, 1L)
        switchTo(buyer)

        val page1 = source.listOrders(role = "buyer", page = 1, pageSize = 2)
        assertEquals(4, page1.total)
        assertEquals(2, page1.items.size)
        assertTrue(page1.hasMore)
        assertEquals(5004L, page1.items.first().id)

        val page2 = source.listOrders(role = "buyer", page = 2, pageSize = 2)
        assertEquals(2, page2.items.size)
        assertFalse(page2.hasMore)

        assertEquals(
            3,
            source.listOrders(role = "buyer", status = OrderStatus.PENDING_ACCEPT).total
        )
        assertEquals(
            1,
            source.listOrders(role = "buyer", status = OrderStatus.ACCEPTED).total
        )
    }

    private suspend fun create(
        items: List<Pair<Int, Int>>,
        key: String = "key-1",
        note: String? = null,
        context: OrderDemoContext = demoContext()
    ): OrderDto = source.createOrder(
        items = items.map { CreateOrderItem(recipeId = it.first.toLong(), quantity = it.second) },
        note = note,
        idempotencyKey = key,
        demoContext = context
    )

    private fun demoContext(): OrderDemoContext = OrderDemoContext(
        sellerId = seller.id,
        sellerName = seller.nickname,
        sellerUsername = seller.username,
        isFriend = true,
        recipes = mapOf(
            101L to OrderDemoRecipe(
                name = "红烧肉",
                imageUrl = "https://cdn.example.com/101.png",
                sellerId = seller.id
            ),
            102L to OrderDemoRecipe(
                name = "番茄炒蛋",
                imageUrl = "",
                sellerId = seller.id
            )
        )
    )

    private fun switchTo(actor: OrderMockActor) {
        currentActor = actor
    }

    private suspend fun expectAuth(code: String, block: suspend () -> Unit) {
        try {
            block()
            fail("expected AuthException $code")
        } catch (e: AuthException) {
            assertEquals(code, e.code)
        }
    }
}
