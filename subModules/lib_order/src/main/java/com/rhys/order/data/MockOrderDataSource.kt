package com.rhys.order.data

import com.example.network.AuthException
import com.example.network.bean.CreateOrderItem
import com.example.network.bean.OrderAction
import com.example.network.bean.OrderDto
import com.example.network.bean.OrderItemCountsDto
import com.example.network.bean.OrderItemDto
import com.example.network.bean.OrderPartyDto
import com.example.network.bean.OrderRecipeDto
import com.example.network.bean.OrderStatus
import com.example.network.bean.PageResponse
import com.example.network.order.InMemoryOrderNotificationStore
import com.example.network.order.OrderNotificationActor
import com.example.network.order.OrderNotificationEvent
import com.example.network.order.OrderNotificationItem
import com.example.network.order.OrderNotificationStore
import com.example.network.order.OrderNotificationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 内存版订单数据源，仅供本地开发、演示与自动化验收。
 *
 * 遵守与 Remote 相同的六态流转、整单汇总、计数、总份数、allowedActions、
 * 幂等、版本与取消规则；[isMock] 恒为 true，UI 必须明确标识演示模式。
 *
 * 双方使用同一份 [MockOrderStore]：同一 App 进程内切换登录账号后，
 * 买方与卖方仍看到同一订单。数据只存在内存，不代表真实后端同步。
 */
class MockOrderDataSource(
    private val store: MockOrderStore = MockOrderStore.shared,
    private val actorProvider: OrderActorProvider = SessionOrderActorProvider,
    private val notificationStore: OrderNotificationStore =
        InMemoryOrderNotificationStore.shared,
    private val now: () -> String = { isoNowUtc() }
) : OrderDataSource {

    override val isMock: Boolean = true

    override suspend fun createOrder(
        items: List<CreateOrderItem>,
        note: String?,
        idempotencyKey: String,
        demoContext: OrderDemoContext?
    ): OrderDto {
        val actor = requireActor()
        val safeKey = OrderDataSource.normalizedIdempotencyKey(idempotencyKey)
        val normalized = OrderDataSource.normalizedCreate(items, note)
        val context = demoContext ?: throw validationError(
            "演示订单需要提供提供方与菜谱快照"
        )
        val seller = resolveSeller(context, normalized.items, actor)

        synchronized(store.lock) {
            val idempotencyScope = "${actor.id}|$safeKey"
            store.idempotency[idempotencyScope]?.let { record ->
                if (record.buyerId == actor.id && record.fingerprint == normalized.fingerprint) {
                    val existing = store.orders[record.orderId] ?: throw orderNotFound()
                    return snapshot(existing, actor)
                }
                throw AuthException(
                    code = "IDEMPOTENCY_KEY_REUSED",
                    message = "该预订请求已提交过且内容不同，请先处理待确认订单",
                    httpStatus = 409
                )
            }

            val orderId = ++store.nextOrderId
            val createdAt = now()
            val itemRecords = normalized.items.mapIndexed { index, item ->
                val recipe = context.recipes.getValue(item.recipeId)
                MockOrderItemRecord(
                    // 订单 ID * 100 + 序号，确定性且跨订单不冲突。
                    id = orderId * ITEM_ID_FACTOR + index + 1,
                    recipeId = item.recipeId,
                    recipeName = recipe.name,
                    imageUrl = recipe.imageUrl,
                    quantity = item.quantity,
                    status = OrderStatus.PENDING_ACCEPT,
                    version = 1L,
                    createdAt = createdAt,
                    updatedAt = createdAt
                )
            }
            val order = MockOrderRecord(
                id = orderId,
                buyer = OrderPartyDto(
                    id = actor.id,
                    username = actor.username,
                    nickname = actor.nickname
                ),
                seller = seller,
                note = normalized.note,
                version = 1L,
                items = itemRecords.toMutableList(),
                createdAt = createdAt,
                updatedAt = createdAt
            )
            store.orders[orderId] = order
            store.idempotency[idempotencyScope] = MockIdempotencyRecord(
                buyerId = actor.id,
                fingerprint = normalized.fingerprint,
                orderId = orderId
            )
            appendNotification(
                type = OrderNotificationType.ORDER_CREATED,
                order = order,
                actorParty = order.buyer,
                recipientId = order.seller.id,
                items = itemRecords.map {
                    notificationItem(it, fromStatus = null, toStatus = OrderStatus.PENDING_ACCEPT)
                }
            )
            return snapshot(order, actor)
        }
    }

    override suspend fun listOrders(
        role: String,
        status: String?,
        page: Int,
        pageSize: Int
    ): PageResponse<OrderDto> {
        val actor = requireActor()
        val safeRole = OrderDataSource.normalizedRole(role)
        val safeStatus = OrderDataSource.normalizedStatus(status)
        val safePage = OrderDataSource.normalizedPage(page)
        val safePageSize = OrderDataSource.normalizedPageSize(pageSize)
        synchronized(store.lock) {
            val filtered = store.orders.values
                .filter { order ->
                    if (safeRole == OrderDataSource.ROLE_BUYER) {
                        order.buyer.id == actor.id
                    } else {
                        order.seller.id == actor.id
                    }
                }
                .filter { order ->
                    safeStatus == null || aggregateStatus(order.items) == safeStatus
                }
                .sortedWith(
                    compareByDescending<MockOrderRecord> { it.createdAt }
                        .thenByDescending { it.id }
                )
            val total = filtered.size
            val from = ((safePage - 1).toLong() * safePageSize)
                .coerceAtMost(total.toLong())
                .toInt()
            val to = (from + safePageSize).coerceAtMost(total)
            val items = if (from >= total) {
                emptyList()
            } else {
                filtered.subList(from, to).map { snapshot(it, actor) }
            }
            return PageResponse(
                items = items,
                page = safePage,
                pageSize = safePageSize,
                total = total
            )
        }
    }

    override suspend fun getOrder(orderId: Long): OrderDto {
        require(orderId > 0) { "orderId must be positive" }
        val actor = requireActor()
        synchronized(store.lock) {
            return snapshot(findAccessibleOrder(orderId, actor), actor)
        }
    }

    override suspend fun actOnItem(
        orderId: Long,
        itemId: Long,
        action: String,
        expectedVersion: Long
    ): OrderDto {
        require(orderId > 0) { "orderId must be positive" }
        require(itemId > 0) { "itemId must be positive" }
        require(expectedVersion > 0) { "expectedVersion must be positive" }
        val safeAction = OrderDataSource.normalizedItemAction(action)
        val actor = requireActor()
        synchronized(store.lock) {
            val order = findAccessibleOrder(orderId, actor)
            val item = order.items.firstOrNull { it.id == itemId } ?: throw itemNotFound()
            if (expectedVersion != item.version) throw versionConflict()

            val fromStatus = item.status
            val newStatus = when (fromStatus) {
                OrderStatus.PENDING_ACCEPT -> when (safeAction) {
                    OrderAction.ACCEPT -> {
                        requireSeller(actor, order)
                        OrderStatus.ACCEPTED
                    }
                    OrderAction.REJECT -> {
                        requireSeller(actor, order)
                        OrderStatus.REJECTED
                    }
                    OrderAction.CANCEL -> {
                        requireBuyer(actor, order)
                        OrderStatus.CANCELLED
                    }
                    else -> throw invalidItemStatus()
                }
                OrderStatus.ACCEPTED -> {
                    if (safeAction != OrderAction.READY) throw invalidItemStatus()
                    requireSeller(actor, order)
                    OrderStatus.WAITING_PICKUP
                }
                OrderStatus.WAITING_PICKUP -> {
                    if (safeAction != OrderAction.COMPLETE) throw invalidItemStatus()
                    requireBuyer(actor, order)
                    OrderStatus.COMPLETED
                }
                else -> throw invalidItemStatus()
            }
            item.status = newStatus
            val notificationType = notificationTypeFor(safeAction)

            val at = now()
            item.version += 1
            item.updatedAt = at
            order.version += 1
            order.updatedAt = at
            appendNotification(
                type = notificationType,
                order = order,
                actorParty = partyOf(actor, order),
                recipientId = recipientOf(actor, order),
                items = listOf(
                    notificationItem(item, fromStatus = fromStatus, toStatus = item.status)
                )
            )
            return snapshot(order, actor)
        }
    }

    override suspend fun cancelOrder(orderId: Long, expectedVersion: Long): OrderDto {
        require(orderId > 0) { "orderId must be positive" }
        require(expectedVersion > 0) { "expectedVersion must be positive" }
        val actor = requireActor()
        synchronized(store.lock) {
            val order = findAccessibleOrder(orderId, actor)
            requireBuyer(actor, order)
            if (expectedVersion != order.version) throw versionConflict()
            if (order.items.any { it.status != OrderStatus.PENDING_ACCEPT }) {
                throw AuthException(
                    code = "ORDER_NOT_CANCELLABLE",
                    message = "仅全部待接受的订单可以取消",
                    httpStatus = 409
                )
            }
            val at = now()
            order.items.forEach { item ->
                item.status = OrderStatus.CANCELLED
                item.version += 1
                item.updatedAt = at
            }
            order.version += 1
            order.updatedAt = at
            appendNotification(
                type = OrderNotificationType.ORDER_CANCELLED,
                order = order,
                actorParty = order.buyer,
                recipientId = order.seller.id,
                items = order.items.map {
                    notificationItem(
                        it,
                        fromStatus = OrderStatus.PENDING_ACCEPT,
                        toStatus = OrderStatus.CANCELLED
                    )
                }
            )
            return snapshot(order, actor)
        }
    }

    private fun resolveSeller(
        context: OrderDemoContext,
        items: List<CreateOrderItem>,
        actor: OrderMockActor
    ): OrderPartyDto {
        val sellers = linkedSetOf<Long>()
        items.forEach { item ->
            val recipe = context.recipes[item.recipeId] ?: throw recipeNotFound()
            if (!recipe.accessible) throw recipeNotFound()
            sellers += recipe.sellerId ?: context.sellerId
        }
        if (sellers.size > 1) {
            throw AuthException(
                code = "MIXED_SELLERS",
                message = "一单只能预订同一位好友的菜品",
                httpStatus = 400
            )
        }
        val sellerId = sellers.firstOrNull() ?: throw validationError("请至少选择一道菜")
        if (sellerId <= 0L) throw validationError("提供方参数不合法")
        if (sellerId == actor.id) {
            throw AuthException(
                code = "CANNOT_ORDER_OWN_RECIPE",
                message = "不能预订自己发布的菜谱",
                httpStatus = 400
            )
        }
        if (!context.isFriend) {
            throw AuthException(
                code = "NOT_FRIENDS",
                message = "你们还不是好友，无法预订",
                httpStatus = 403
            )
        }
        val firstRecipe = context.recipes[items.first().recipeId]
            ?: throw recipeNotFound()
        val sellerName = if (sellerId == context.sellerId) {
            context.sellerName
        } else {
            firstRecipe.sellerName?.takeIf { it.isNotBlank() } ?: "好友$sellerId"
        }
        val sellerUsername = if (sellerId == context.sellerId) {
            context.sellerUsername
        } else {
            firstRecipe.sellerUsername?.takeIf { it.isNotBlank() } ?: "seller_$sellerId"
        }
        return OrderPartyDto(
            id = sellerId,
            username = sellerUsername,
            nickname = sellerName
        )
    }

    private fun findAccessibleOrder(orderId: Long, actor: OrderMockActor): MockOrderRecord {
        val order = store.orders[orderId] ?: throw orderNotFound()
        // 第三方访问与不存在同样返回 404，不泄露订单是否存在。
        if (actor.id != order.buyer.id && actor.id != order.seller.id) {
            throw orderNotFound()
        }
        return order
    }

    private fun snapshot(order: MockOrderRecord, actor: OrderMockActor): OrderDto {
        val items = order.items.map { item ->
            OrderItemDto(
                id = item.id,
                recipe = OrderRecipeDto(
                    id = item.recipeId,
                    name = item.recipeName,
                    imageUrl = item.imageUrl
                ),
                quantity = item.quantity,
                status = item.status,
                version = item.version,
                allowedActions = itemActions(actor, order, item),
                createdAt = item.createdAt,
                updatedAt = item.updatedAt
            )
        }
        return OrderDto(
            id = order.id,
            status = aggregateStatus(order.items),
            version = order.version,
            note = order.note,
            buyer = order.buyer,
            seller = order.seller,
            items = items,
            itemCounts = countsOf(order.items),
            totalQuantity = order.items.sumOf { it.quantity.toLong() },
            allowedActions = orderActions(actor, order),
            createdAt = order.createdAt,
            updatedAt = order.updatedAt
        )
    }

    private fun aggregateStatus(items: List<MockOrderItemRecord>): String {
        val statuses = items.map { it.status }
        return when {
            statuses.any { it == OrderStatus.PENDING_ACCEPT } -> OrderStatus.PENDING_ACCEPT
            statuses.any { it == OrderStatus.ACCEPTED } -> OrderStatus.ACCEPTED
            statuses.any { it == OrderStatus.WAITING_PICKUP } -> OrderStatus.WAITING_PICKUP
            statuses.any { it == OrderStatus.COMPLETED } -> OrderStatus.COMPLETED
            statuses.isNotEmpty() && statuses.all { it == OrderStatus.REJECTED } ->
                OrderStatus.REJECTED
            statuses.isNotEmpty() && statuses.all { it == OrderStatus.CANCELLED } ->
                OrderStatus.CANCELLED
            else -> OrderStatus.CLOSED
        }
    }

    private fun countsOf(items: List<MockOrderItemRecord>): OrderItemCountsDto =
        OrderItemCountsDto(
            total = items.size,
            pendingAccept = items.count { it.status == OrderStatus.PENDING_ACCEPT },
            accepted = items.count { it.status == OrderStatus.ACCEPTED },
            waitingPickup = items.count { it.status == OrderStatus.WAITING_PICKUP },
            completed = items.count { it.status == OrderStatus.COMPLETED },
            rejected = items.count { it.status == OrderStatus.REJECTED },
            cancelled = items.count { it.status == OrderStatus.CANCELLED }
        )

    private fun itemActions(
        actor: OrderMockActor,
        order: MockOrderRecord,
        item: MockOrderItemRecord
    ): List<String> {
        val isBuyer = actor.id == order.buyer.id
        val isSeller = actor.id == order.seller.id
        if (!isBuyer && !isSeller) return emptyList()
        return when (item.status) {
            OrderStatus.PENDING_ACCEPT -> when {
                isSeller -> listOf(OrderAction.ACCEPT, OrderAction.REJECT)
                else -> listOf(OrderAction.CANCEL)
            }
            OrderStatus.ACCEPTED -> if (isSeller) listOf(OrderAction.READY) else emptyList()
            OrderStatus.WAITING_PICKUP -> if (isBuyer) listOf(OrderAction.COMPLETE) else emptyList()
            else -> emptyList()
        }
    }

    private fun orderActions(actor: OrderMockActor, order: MockOrderRecord): List<String> {
        val isBuyer = actor.id == order.buyer.id
        val allPending = order.items.isNotEmpty() &&
            order.items.all { it.status == OrderStatus.PENDING_ACCEPT }
        return if (isBuyer && allPending) listOf(OrderAction.CANCEL_ALL) else emptyList()
    }

    private fun appendNotification(
        type: String,
        order: MockOrderRecord,
        actorParty: OrderPartyDto,
        recipientId: Long,
        items: List<OrderNotificationItem>
    ) {
        notificationStore.append(
            OrderNotificationEvent(
                type = type,
                orderId = order.id,
                orderStatus = aggregateStatus(order.items),
                actor = OrderNotificationActor(
                    id = actorParty.id,
                    nickname = actorParty.displayName
                ),
                recipientId = recipientId,
                items = items,
                createdAt = now()
            )
        )
    }

    private fun notificationItem(
        item: MockOrderItemRecord,
        fromStatus: String?,
        toStatus: String
    ): OrderNotificationItem = OrderNotificationItem(
        itemId = item.id,
        recipeId = item.recipeId,
        recipeName = item.recipeName,
        quantity = item.quantity,
        fromStatus = fromStatus,
        toStatus = toStatus
    )

    private fun notificationTypeFor(action: String): String = when (action) {
        OrderAction.ACCEPT -> OrderNotificationType.ORDER_ACCEPTED
        OrderAction.REJECT -> OrderNotificationType.ORDER_REJECTED
        OrderAction.READY -> OrderNotificationType.ORDER_READY
        OrderAction.CANCEL -> OrderNotificationType.ORDER_CANCELLED
        OrderAction.COMPLETE -> OrderNotificationType.ORDER_COMPLETED
        else -> OrderNotificationType.ORDER_CREATED
    }

    private fun partyOf(actor: OrderMockActor, order: MockOrderRecord): OrderPartyDto =
        if (actor.id == order.buyer.id) order.buyer else order.seller

    private fun recipientOf(actor: OrderMockActor, order: MockOrderRecord): Long =
        if (actor.id == order.buyer.id) order.seller.id else order.buyer.id

    private fun requireActor(): OrderMockActor = actorProvider.currentActor()
        ?: throw AuthException(
            code = "UNAUTHORIZED",
            message = "登录后才能使用订单功能",
            httpStatus = 401
        )

    private fun requireSeller(actor: OrderMockActor, order: MockOrderRecord) {
        if (actor.id != order.seller.id) throw forbidden()
    }

    private fun requireBuyer(actor: OrderMockActor, order: MockOrderRecord) {
        if (actor.id != order.buyer.id) throw forbidden()
    }

    private fun forbidden(): AuthException = AuthException(
        code = "FORBIDDEN_ORDER_ACTION",
        message = "没有权限执行该订单操作",
        httpStatus = 403
    )

    private fun invalidItemStatus(): AuthException = AuthException(
        code = "INVALID_ORDER_ITEM_STATUS",
        message = "当前菜品状态不允许该操作",
        httpStatus = 409
    )

    private fun versionConflict(): AuthException = AuthException(
        code = "ORDER_VERSION_CONFLICT",
        message = "订单状态已更新，请刷新后重试",
        httpStatus = 409
    )

    private fun orderNotFound(): AuthException = AuthException(
        code = "ORDER_NOT_FOUND",
        message = "订单不存在或你没有访问权限",
        httpStatus = 404
    )

    private fun itemNotFound(): AuthException = AuthException(
        code = "ORDER_ITEM_NOT_FOUND",
        message = "订单明细不存在或不属于该订单",
        httpStatus = 404
    )

    private fun recipeNotFound(): AuthException = AuthException(
        code = "RECIPE_NOT_FOUND",
        message = "菜谱不存在或当前不可预订",
        httpStatus = 404
    )

    private fun validationError(message: String): AuthException = AuthException(
        code = "VALIDATION_ERROR",
        message = message,
        httpStatus = 400
    )

    companion object {
        /** 明细 ID = 订单 ID * 100 + 序号，确定性且可读。 */
        const val ITEM_ID_FACTOR = 100L

        /** 首个演示订单 ID；后续按 MockOrderStore 顺序递增。 */
        const val FIRST_ORDER_ID = MockOrderStore.FIRST_ORDER_ID
    }
}

private fun isoNowUtc(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date())
}
