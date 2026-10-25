package com.rhys.chat.data

import com.example.network.ApiClient
import com.example.network.AuthException
import com.example.network.ChatErrorMapper
import com.example.network.NetworkConfig
import com.example.network.api.NotificationApi
import com.example.network.bean.PageResponse
import com.example.network.order.InMemoryOrderNotificationStore
import com.example.network.order.OrderDataSourceConfig
import com.example.network.order.OrderDataSourceKind
import com.example.network.order.OrderNotificationEvent
import com.example.network.order.OrderNotificationStore
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.rhys.chat.domain.ChatNotification
import kotlinx.coroutines.CancellationException

/**
 * 预订通知页的数据访问层。
 *
 * - 数据源跟随订单：`OrderDataSourceConfig` 决定 Mock/Remote，**不使用 `chatDataSource`**；
 * - Remote 走 `GET notifications?category=order` 页码分页，失败不回退 Mock；
 * - Mock 读取 `InMemoryOrderNotificationStore` 中当前接收方的事件，按时间倒序内存分页；
 * - 缓存按 `ownerId + Mock/Remote + baseUrl` 隔离，账号或环境变化时清空，避免串号与
 *   Mock/远端通知 ID 碰撞；
 * - 本期不读写未读/已读状态，不新增本地订单通知表，也不改动聊天 Room 缓存。
 */
class OrderNotificationRepository(
    private val dataSourceKind: OrderDataSourceKind = OrderDataSourceConfig.current().kind,
    private val notificationApi: NotificationApi = ApiClient.notificationApi,
    private val mockStore: OrderNotificationStore = InMemoryOrderNotificationStore.shared,
    private val ownerProvider: () -> Long? = {
        (SessionManager.state.value as? AuthState.LoggedIn)?.user?.id
    },
    private val baseUrlProvider: () -> String = { NetworkConfig.baseUrl }
) {

    /** 当前是否为本地演示数据源；页面必须据此展示演示标识。 */
    val isMock: Boolean
        get() = dataSourceKind == OrderDataSourceKind.MOCK

    private val cacheLock = Any()
    private var cachedScope: String? = null
    private val pageCache = mutableMapOf<Int, PageResponse<ChatNotification>>()

    /**
     * 拉取一页订单通知。
     *
     * 未登录抛 `UNAUTHORIZED`；任意失败都原样抛出可展示异常，不返回空成功页。
     */
    suspend fun fetch(page: Int, pageSize: Int): PageResponse<ChatNotification> {
        val ownerId = ownerProvider() ?: throw unauthorized("登录后才能查看预订通知")
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val scope = scopeOf(ownerId)
        resetCacheIfScopeChanged(scope)

        val result = if (isMock) {
            fetchMock(ownerId, safePage, safePageSize)
        } else {
            fetchRemote(safePage, safePageSize)
        }

        // 数据源返回期间账号或环境变化：旧结果既不能返回给新账号，也不能写入新作用域缓存。
        if (ownerProvider() != ownerId || scopeOf(ownerId) != scope) {
            throw unauthorized("登录状态已变更，请刷新后重试")
        }
        synchronized(cacheLock) {
            if (cachedScope == scope) pageCache[safePage] = result
        }
        return result
    }

    /** 当前作用域下已成功拉取过的页面；账号/环境变化后返回 null。 */
    fun cached(page: Int, pageSize: Int): PageResponse<ChatNotification>? {
        val ownerId = ownerProvider() ?: return null
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        synchronized(cacheLock) {
            val scope = scopeOf(ownerId)
            if (cachedScope != scope) {
                pageCache.clear()
                cachedScope = scope
                return null
            }
            return pageCache[safePage]?.takeIf { it.pageSize == safePageSize }
        }
    }

    /** 主动清空缓存；页面在账号切换/退出时重建仓库，亦可调用本方法。 */
    fun invalidate() {
        synchronized(cacheLock) {
            pageCache.clear()
            cachedScope = null
        }
    }

    private fun fetchMock(ownerId: Long, page: Int, pageSize: Int): PageResponse<ChatNotification> {
        val events = mockStore.list(ownerId, OrderNotificationStore.MAX_LIMIT)
            .filter { it.recipientId == ownerId }
            .sortedWith(
                compareByDescending<OrderNotificationEvent> { it.createdAt }
                    .thenByDescending { it.id }
            )
        val total = events.size
        val from = ((page - 1).toLong() * pageSize).coerceAtMost(total.toLong()).toInt()
        val to = (from + pageSize).coerceAtMost(total)
        val items = if (from >= total) {
            emptyList()
        } else {
            events.subList(from, to).mapNotNull { event -> OrderNotificationMapper.map(event) }
        }
        return PageResponse(items = items, page = page, pageSize = pageSize, total = total)
    }

    private suspend fun fetchRemote(page: Int, pageSize: Int): PageResponse<ChatNotification> {
        val response = try {
            notificationApi.listOrderNotifications(
                category = CATEGORY_ORDER,
                page = page,
                pageSize = pageSize
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ChatErrorMapper.map(e, UNAUTHORIZED_MESSAGE)
        }
        val items = response.items.orEmpty()
            .filterNotNull()
            .mapNotNull { dto -> OrderNotificationMapper.map(dto) }
        return PageResponse(
            items = items,
            page = response.page.takeIf { it > 0 } ?: page,
            pageSize = response.pageSize.takeIf { it > 0 } ?: pageSize,
            total = response.total.coerceAtLeast(0)
        )
    }

    private fun resetCacheIfScopeChanged(scope: String) {
        synchronized(cacheLock) {
            if (cachedScope != scope) {
                pageCache.clear()
                cachedScope = scope
            }
        }
    }

    private fun scopeOf(ownerId: Long): String =
        "$ownerId|${if (isMock) SOURCE_MOCK else SOURCE_REMOTE}|${baseUrlProvider()}"

    private fun unauthorized(message: String): AuthException = AuthException(
        code = "UNAUTHORIZED",
        message = message,
        httpStatus = 401
    )

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50

        private const val CATEGORY_ORDER = "order"
        private const val SOURCE_MOCK = "mock"
        private const val SOURCE_REMOTE = "remote"
        private const val UNAUTHORIZED_MESSAGE = "登录已失效，请重新登录"
    }
}
