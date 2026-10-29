package com.rhys.chat.data

import com.example.network.AuthException
import com.example.network.api.NotificationApi
import com.example.network.bean.ChatNotificationDto
import com.example.network.bean.CursorPage
import com.example.network.bean.OrderNotificationActorDto
import com.example.network.bean.OrderNotificationDto
import com.example.network.bean.OrderNotificationFromUserDto
import com.example.network.bean.OrderNotificationItemDto
import com.example.network.bean.OrderNotificationPayloadDto
import com.example.network.bean.PageResponse
import com.example.network.order.InMemoryOrderNotificationStore
import com.example.network.order.OrderDataSourceConfig
import com.example.network.order.OrderDataSourceKind
import com.example.network.order.OrderNotificationActor
import com.example.network.order.OrderNotificationEvent
import com.example.network.order.OrderNotificationItem
import com.example.network.order.OrderNotificationStore
import com.example.network.order.OrderNotificationType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 订单通知 Repository 的分页、数据源选择、账号隔离与错误路径。
 *
 * Remote 使用假 API，Mock 使用独立内存 store；不访问真实网络或数据库。
 */
class OrderNotificationRepositoryTest {

    private var ownerId: Long? = 1L

    @Test
    fun `remote fetch uses category order and maps two pages`() = runBlocking {
        val api = FakeNotificationApi().apply {
            pages[1] = PageResponse(items = listOf(dto(1L), dto(2L)), page = 1, pageSize = 2, total = 3)
            pages[2] = PageResponse(items = listOf(dto(3L)), page = 2, pageSize = 2, total = 3)
        }
        val repository = newRepository(OrderDataSourceKind.REMOTE, api)

        val first = repository.fetch(1, 2)
        assertEquals(2, first.items.size)
        assertEquals(3, first.total)
        assertTrue(first.hasMore)

        val second = repository.fetch(2, 2)
        assertEquals(1, second.items.size)
        assertFalse(second.hasMore)

        assertEquals(listOf(1, 2), api.requestedPages)
        assertEquals(listOf(2, 2), api.requestedPageSizes)
        assertEquals("order", api.lastCategory)
    }

    @Test
    fun `all notifications request omits category and maps recipe share`() = runBlocking {
        val api = FakeNotificationApi().apply {
            allPages[1] = PageResponse(
                items = listOf(shareDto(21L, recipeId = 88L)),
                page = 1,
                pageSize = 20,
                total = 1
            )
        }
        val repository = newRepository(OrderDataSourceKind.REMOTE, api)

        val page = repository.fetchAll(1, 20)

        assertEquals(1, page.items.size)
        val item = page.items.first()
        assertEquals("菜谱分享", item.title)
        assertEquals("「小林」分享了「番茄炒蛋」", item.body)
        assertEquals(88L, item.recipeId)
        assertNull(item.orderId)
        assertEquals(listOf(1), api.allRequestedPages)
        assertEquals(listOf(20), api.allRequestedPageSizes)
        // 全部通知与仅订单是两条不同请求：all 路径不得触发 category=order。
        assertTrue(api.requestedPages.isEmpty())
        assertNull(api.lastCategory)
    }

    @Test
    fun `cache scope separates all and order notifications`() = runBlocking {
        val api = FakeNotificationApi().apply {
            pages[1] = PageResponse(items = listOf(dto(1L)), page = 1, pageSize = 20, total = 1)
            allPages[1] = PageResponse(
                items = listOf(shareDto(21L, recipeId = 88L)),
                page = 1,
                pageSize = 20,
                total = 1
            )
        }
        val repository = newRepository(OrderDataSourceKind.REMOTE, api)

        repository.fetch(1, 20)
        assertNotNull(repository.cached(1, 20, NotificationScope.ORDER))
        assertNull(repository.cached(1, 20, NotificationScope.ALL))

        repository.fetchAll(1, 20)
        assertNotNull(repository.cached(1, 20, NotificationScope.ALL))
        assertNull(repository.cached(1, 20, NotificationScope.ORDER))

        repository.fetch(1, 20)
        assertNotNull(repository.cached(1, 20, NotificationScope.ORDER))
        assertNull(repository.cached(1, 20, NotificationScope.ALL))
    }

    @Test
    fun `mock store isolates owners and paginates newest first`() = runBlocking {
        val store = InMemoryOrderNotificationStore()
        store.append(event(1L, OrderNotificationType.ORDER_CREATED, 101L, "2026-10-20T09:00:00Z"))
        store.append(event(1L, OrderNotificationType.ORDER_ACCEPTED, 102L, "2026-10-20T09:01:00Z"))
        store.append(event(1L, OrderNotificationType.ORDER_READY, 103L, "2026-10-20T09:02:00Z"))
        store.append(event(2L, OrderNotificationType.ORDER_CREATED, 201L, "2026-10-20T09:03:00Z"))
        store.append(event(2L, OrderNotificationType.ORDER_CANCELLED, 202L, "2026-10-20T09:04:00Z"))
        val repository = newRepository(OrderDataSourceKind.MOCK, store = store)

        val first = repository.fetch(1, 2)
        assertEquals(2, first.items.size)
        assertEquals(3, first.total)
        assertTrue(first.hasMore)
        assertEquals(103L, first.items.first().orderId)

        val second = repository.fetch(2, 2)
        assertEquals(1, second.items.size)
        assertFalse(second.hasMore)
        assertEquals(101L, second.items.first().orderId)

        ownerId = 2L
        val other = repository.fetch(1, 20)
        assertEquals(2, other.items.size)
        assertEquals(2, other.total)
        assertEquals(202L, other.items.first().orderId)

        ownerId = 1L
        assertNull(repository.cached(1, 2))
    }

    @Test
    fun `isMock follows order data source config`() {
        assertTrue(newRepository(OrderDataSourceKind.MOCK).isMock)
        assertFalse(newRepository(OrderDataSourceKind.REMOTE).isMock)

        val defaultKind = OrderDataSourceConfig.current().kind
        val defaultRepository = OrderNotificationRepository(notificationApi = FakeNotificationApi())
        assertEquals(defaultKind == OrderDataSourceKind.MOCK, defaultRepository.isMock)
    }

    @Test
    fun `fetch without login throws unauthorized without calling api`() {
        ownerId = null
        val api = FakeNotificationApi()
        val repository = newRepository(OrderDataSourceKind.REMOTE, api)

        val error = runCatching {
            runBlocking { repository.fetch(1, 20) }
        }.exceptionOrNull()

        assertTrue(error is AuthException)
        assertEquals("UNAUTHORIZED", (error as AuthException).code)
        assertEquals(401, error.httpStatus)
        assertTrue(api.requestedPages.isEmpty())
    }

    @Test
    fun `remote failure maps to readable error and never falls back to mock`() {
        val api = FakeNotificationApi().apply { failure = IOException("boom") }
        val store = InMemoryOrderNotificationStore().apply {
            append(event(1L, OrderNotificationType.ORDER_CREATED, 101L, "2026-10-20T09:00:00Z"))
        }
        val repository = newRepository(OrderDataSourceKind.REMOTE, api, store)

        val error = runCatching {
            runBlocking { repository.fetch(1, 20) }
        }.exceptionOrNull()

        assertTrue(error is AuthException)
        assertEquals("NETWORK_ERROR", (error as AuthException).code)
        assertTrue((error.message ?: "").contains("无法连接服务器"))
        assertEquals(1, api.requestedPages.size)
    }

    @Test
    fun `remote unauthorized is propagated`() {
        val api = FakeNotificationApi().apply {
            failure = AuthException(
                code = "UNAUTHORIZED",
                message = "登录已失效，请重新登录",
                httpStatus = 401
            )
        }
        val repository = newRepository(OrderDataSourceKind.REMOTE, api)

        val error = runCatching {
            runBlocking { repository.fetch(1, 20) }
        }.exceptionOrNull()

        assertTrue(error is AuthException)
        assertTrue((error as AuthException).isUnauthorized)
    }

    @Test
    fun `page and page size are normalized before calling api`() = runBlocking {
        val api = FakeNotificationApi()
        val repository = newRepository(OrderDataSourceKind.REMOTE, api)

        repository.fetch(0, 0)
        assertEquals(listOf(1), api.requestedPages)
        assertEquals(listOf(1), api.requestedPageSizes)

        repository.fetch(-3, 999)
        assertEquals(1, api.requestedPages.last())
        assertEquals(50, api.requestedPageSizes.last())
    }

    @Test
    fun `cache is scoped by owner source and base url`() = runBlocking {
        var baseUrl = "http://one.local/api/v1/"
        val api = FakeNotificationApi().apply {
            pages[1] = PageResponse(items = listOf(dto(1L)), page = 1, pageSize = 20, total = 1)
        }
        val repository = newRepository(
            OrderDataSourceKind.REMOTE,
            api,
            baseUrlProvider = { baseUrl }
        )

        repository.fetch(1, 20)
        assertNotNull(repository.cached(1, 20))

        baseUrl = "http://two.local/api/v1/"
        assertNull(repository.cached(1, 20))

        ownerId = 2L
        assertNull(repository.cached(1, 20))
    }

    @Test
    fun `mock returns empty page for other recipient`() = runBlocking {
        val store = InMemoryOrderNotificationStore().apply {
            append(event(2L, OrderNotificationType.ORDER_CREATED, 201L, "2026-10-20T09:00:00Z"))
        }
        val repository = newRepository(OrderDataSourceKind.MOCK, store = store)

        val page = repository.fetch(1, 20)
        assertTrue(page.items.isEmpty())
        assertEquals(0, page.total)
        assertFalse(page.hasMore)
    }

    private fun newRepository(
        kind: OrderDataSourceKind,
        api: NotificationApi = FakeNotificationApi(),
        store: OrderNotificationStore = InMemoryOrderNotificationStore(),
        baseUrlProvider: () -> String = { "http://test.local/api/v1/" }
    ): OrderNotificationRepository = OrderNotificationRepository(
        dataSourceKind = kind,
        notificationApi = api,
        mockStore = store,
        ownerProvider = { ownerId },
        baseUrlProvider = baseUrlProvider
    )

    private fun dto(
        id: Long,
        type: String = OrderNotificationType.ORDER_CREATED,
        orderId: Long = 100L
    ): OrderNotificationDto = OrderNotificationDto(
        id = id,
        type = type,
        payload = OrderNotificationPayloadDto(
            schemaVersion = 2,
            orderId = orderId,
            orderStatus = "PENDING_ACCEPT",
            actor = OrderNotificationActorDto(id = 9L, nickname = "小林"),
            items = listOf(
                OrderNotificationItemDto(
                    itemId = 1L,
                    recipeId = 1L,
                    recipeName = "红烧肉",
                    quantity = 1,
                    fromStatus = null,
                    toStatus = "PENDING_ACCEPT"
                )
            )
        ),
        createdAt = "2026-10-20T09:10:00Z"
    )

    private fun shareDto(
        id: Long,
        recipeId: Long,
        nickname: String = "小林",
        recipeName: String = "番茄炒蛋"
    ): OrderNotificationDto = OrderNotificationDto(
        id = id,
        type = "recipe_share",
        payload = OrderNotificationPayloadDto(
            recipeId = recipeId,
            recipeName = recipeName,
            fromUser = OrderNotificationFromUserDto(
                id = 9L,
                username = "lin_xiaochu",
                nickname = nickname
            )
        ),
        createdAt = "2026-10-20T09:10:00Z"
    )

    private fun event(
        recipientId: Long,
        type: String,
        orderId: Long,
        createdAt: String
    ): OrderNotificationEvent = OrderNotificationEvent(
        type = type,
        orderId = orderId,
        orderStatus = "PENDING_ACCEPT",
        actor = OrderNotificationActor(id = 9L, nickname = "小林"),
        recipientId = recipientId,
        items = listOf(
            OrderNotificationItem(
                itemId = orderId * 100 + 1,
                recipeId = 1L,
                recipeName = "红烧肉",
                quantity = 1,
                fromStatus = null,
                toStatus = "PENDING_ACCEPT"
            )
        ),
        createdAt = createdAt
    )

    private class FakeNotificationApi : NotificationApi {

        val pages = mutableMapOf<Int, PageResponse<OrderNotificationDto>>()
        val requestedPages = mutableListOf<Int>()
        val requestedPageSizes = mutableListOf<Int>()
        var lastCategory: String? = null
        var failure: Throwable? = null

        val allPages = mutableMapOf<Int, PageResponse<OrderNotificationDto>>()
        val allRequestedPages = mutableListOf<Int>()
        val allRequestedPageSizes = mutableListOf<Int>()
        var allFailure: Throwable? = null

        override suspend fun listNotifications(
            limit: Int,
            cursor: String?
        ): CursorPage<ChatNotificationDto> = CursorPage(emptyList(), null)

        override suspend fun listOrderNotifications(
            category: String,
            page: Int,
            pageSize: Int
        ): PageResponse<OrderNotificationDto> {
            lastCategory = category
            requestedPages += page
            requestedPageSizes += pageSize
            failure?.let { throw it }
            return pages[page] ?: PageResponse(
                items = emptyList(),
                page = page,
                pageSize = pageSize,
                total = 0
            )
        }

        override suspend fun listAllNotifications(
            page: Int,
            pageSize: Int
        ): PageResponse<OrderNotificationDto> {
            allRequestedPages += page
            allRequestedPageSizes += pageSize
            allFailure?.let { throw it }
            return allPages[page] ?: PageResponse(
                items = emptyList(),
                page = page,
                pageSize = pageSize,
                total = 0
            )
        }
    }
}
