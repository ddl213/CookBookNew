package com.rhys.order.data

import com.example.network.AuthException
import com.example.network.NetworkConfig
import com.example.network.bean.CreateOrderItem
import com.example.network.bean.OrderDto
import com.example.network.bean.PageResponse
import com.example.network.order.OrderDataSourceKind
import com.rhys.order.data.pending.KeystoreOrderPendingCipher
import com.rhys.order.data.pending.MmkvOrderPendingStore
import com.rhys.order.data.pending.OrderPendingCipher
import com.rhys.order.data.pending.OrderPendingCodec
import com.rhys.order.data.pending.OrderPendingDecryption
import com.rhys.order.data.pending.OrderPendingScope
import com.rhys.order.data.pending.OrderPendingStore
import com.rhys.order.data.pending.PendingOrderCreate
import kotlinx.coroutines.CancellationException
import java.util.UUID

/**
 * 订单数据访问层。
 *
 * 页面应经本类读写订单，不直接依赖 Retrofit 或 Mock 数据源。
 * 列表/详情/动作只透传数据源结果，不做本地乐观改状态；版本冲突原样上抛。
 *
 * 创建幂等与超时恢复：
 * - 发送前生成 UUID 幂等键，并持久化“键 + 规范化请求”；
 * - 持久化按 owner + 数据源类型 + baseUrl 隔离，请求内容 AES-256-GCM 加密；
 * - 网络超时/IO/5xx/未知异常保留 pending 并抛 [OrderCreatePendingException]，
 *   不自动换键或重复下单；成功或明确 4xx 后清理 pending；
 * - [retryPendingCreate] 必须用原键原内容重放；已有 pending 且新内容不同时
 *   抛 [OrderPendingConflictException]，由 UI 先处理待确认请求。
 *
 * 构造参数默认面向生产；测试注入内存 store、JVM cipher、固定 owner/baseUrl 与时钟。
 */
class OrderRepository(
    private val dataSource: OrderDataSource = OrderDataSourceFactory.create(),
    private val ownerProvider: OrderOwnerProvider = SessionOrderOwnerProvider,
    private val pendingStore: OrderPendingStore = MmkvOrderPendingStore(),
    private val cipher: OrderPendingCipher = KeystoreOrderPendingCipher(),
    private val idempotencyKeyFactory: () -> String = { UUID.randomUUID().toString() },
    private val baseUrlProvider: () -> String = { NetworkConfig.baseUrl },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /** 当前数据源是否为演示模式；UI 必须据此展示演示标识。 */
    val isMock: Boolean
        get() = dataSource.isMock

    /**
     * 创建订单。
     *
     * 已有同 scope pending 时：内容相同则用原键重放；内容不同或不可读则拒绝，
     * 不静默创建新单。[demoContext] 只对 Mock 生效，Remote 忽略。
     */
    suspend fun createOrder(
        items: List<CreateOrderItem>,
        note: String?,
        demoContext: OrderDemoContext? = null
    ): OrderDto {
        val owner = requireOwner()
        val normalized = OrderDataSource.normalizedCreate(items, note)
        val scope = scopeFor(owner)

        val existing = readPending(scope)
        if (existing != null) {
            if (!existing.readable ||
                existing.items != normalized.items ||
                existing.note != normalized.note
            ) {
                throw OrderPendingConflictException(existing)
            }
            // 同键同内容：直接重放，不生成新键。
            return sendCreate(existing, demoContext, scope)
        }

        val key = OrderDataSource.normalizedIdempotencyKey(idempotencyKeyFactory())
        val pending = PendingOrderCreate(
            idempotencyKey = key,
            items = normalized.items,
            note = normalized.note,
            createdAt = clock(),
            readable = true
        )
        // 先落盘再发送；加密失败会直接抛出，不会发出无键请求。
        persistPending(scope, pending)
        return sendCreate(pending, demoContext, scope)
    }

    /**
     * 用原幂等键与规范化内容重放待确认创建。
     *
     * 没有 pending 时抛 `ORDER_PENDING_NOT_FOUND`；密文不可读时抛
     * `ORDER_PENDING_UNREADABLE`，提示先放弃；网络仍失败则继续保留 pending。
     */
    suspend fun retryPendingCreate(demoContext: OrderDemoContext? = null): OrderDto {
        val owner = requireOwner()
        val scope = scopeFor(owner)
        val pending = readPending(scope) ?: throw AuthException(
            code = "ORDER_PENDING_NOT_FOUND",
            message = "没有待确认的预订请求",
            httpStatus = 404
        )
        if (!pending.readable) throw AuthException(
            code = "ORDER_PENDING_UNREADABLE",
            message = "上一笔预订请求无法读取，请放弃后重新提交",
            httpStatus = 409
        )
        return sendCreate(pending, demoContext, scope)
    }

    /** 当前 owner/数据源/环境下的待确认请求；未登录返回 null，不读取他人数据。 */
    fun pendingCreate(): PendingOrderCreate? {
        val owner = ownerProvider.currentOwnerId() ?: return null
        return readPending(scopeFor(owner))
    }

    /** 用户明确放弃待确认请求；未登录时不做任何事。 */
    fun discardPending() {
        val owner = ownerProvider.currentOwnerId() ?: return
        pendingStore.clear(scopeFor(owner))
    }

    suspend fun listOrders(
        role: String = OrderDataSource.ROLE_BUYER,
        status: String? = null,
        page: Int = 1,
        pageSize: Int = OrderDataSource.DEFAULT_PAGE_SIZE
    ): PageResponse<OrderDto> = dataSource.listOrders(role, status, page, pageSize)

    suspend fun getOrder(orderId: Long): OrderDto = dataSource.getOrder(orderId)

    suspend fun actOnItem(
        orderId: Long,
        itemId: Long,
        action: String,
        expectedVersion: Long
    ): OrderDto = dataSource.actOnItem(orderId, itemId, action, expectedVersion)

    suspend fun cancelOrder(orderId: Long, expectedVersion: Long): OrderDto =
        dataSource.cancelOrder(orderId, expectedVersion)

    private suspend fun sendCreate(
        pending: PendingOrderCreate,
        demoContext: OrderDemoContext?,
        scope: OrderPendingScope
    ): OrderDto {
        val order = try {
            dataSource.createOrder(
                items = pending.items,
                note = pending.note,
                idempotencyKey = pending.idempotencyKey,
                demoContext = demoContext
            )
        } catch (e: CancellationException) {
            // 取消可能发生在请求已发出之后：保留 pending，等待用户确认/重试。
            throw e
        } catch (e: Exception) {
            if (isDefiniteRejection(e)) {
                // 明确 4xx 表示服务端未创建订单，可安全清理等待新的请求。
                pendingStore.clear(scope)
                throw e
            }
            // 超时/IO/5xx/未知：结果未知，保留原键原内容供 UI 确认。
            throw OrderCreatePendingException(pending = pending, cause = e)
        }
        // 成功后清理 pending；清理失败不回滚已成功结果（最坏只是后续需人工放弃）。
        runCatching { pendingStore.clear(scope) }
        return order
    }

    private fun readPending(scope: OrderPendingScope): PendingOrderCreate? {
        val raw = pendingStore.read(scope) ?: return null
        val separator = raw.indexOf(PAYLOAD_SEPARATOR)
        if (separator <= 0) {
            return PendingOrderCreate(idempotencyKey = raw, readable = false)
        }
        val key = raw.substring(0, separator)
        if (key.isBlank()) {
            return PendingOrderCreate(idempotencyKey = raw, readable = false)
        }
        val cipherText = raw.substring(separator + 1)
        if (cipherText.isEmpty()) {
            return PendingOrderCreate(idempotencyKey = key, readable = false)
        }
        val plaintext = when (val result = cipher.decrypt(cipherText)) {
            is OrderPendingDecryption.Success -> result.value
            OrderPendingDecryption.Unavailable ->
                return PendingOrderCreate(idempotencyKey = key, readable = false)
        }
        val decoded = OrderPendingCodec.decode(plaintext)
            ?: return PendingOrderCreate(idempotencyKey = key, readable = false)
        return decoded.copy(idempotencyKey = key)
    }

    private fun persistPending(scope: OrderPendingScope, pending: PendingOrderCreate) {
        val encoded = OrderPendingCodec.encode(pending)
        val cipherText = cipher.encrypt(encoded)
        pendingStore.write(
            scope,
            "${pending.idempotencyKey}$PAYLOAD_SEPARATOR$cipherText"
        )
    }

    private fun scopeFor(ownerId: Long): OrderPendingScope = OrderPendingScope(
        ownerId = ownerId,
        kind = if (dataSource.isMock) OrderDataSourceKind.MOCK else OrderDataSourceKind.REMOTE,
        baseUrl = baseUrlProvider()
    )

    private fun requireOwner(): Long = ownerProvider.currentOwnerId() ?: throw AuthException(
        code = "UNAUTHORIZED",
        message = "登录后才能使用订单功能",
        httpStatus = 401
    )

    /** 只有带 4xx 的已映射错误能证明服务端未创建订单。 */
    private fun isDefiniteRejection(e: Exception): Boolean {
        val auth = e as? AuthException ?: return false
        val status = auth.httpStatus ?: return false
        return status in 400..499
    }

    private companion object {
        const val PAYLOAD_SEPARATOR = '|'
    }
}

/**
 * 创建结果待确认：请求已可能到达服务端，但客户端未拿到确定的成功/失败。
 *
 * UI 必须提示“结果待确认”，调用 [OrderRepository.retryPendingCreate] 用原键重放，
 * 或经用户确认后 [OrderRepository.discardPending]；不得自动换键重新下单。
 */
class OrderCreatePendingException(
    val pending: PendingOrderCreate,
    message: String = "预订结果待确认，请稍后确认是否成功",
    cause: Throwable? = null
) : Exception(message, cause) {

    val idempotencyKey: String
        get() = pending.idempotencyKey
}

/**
 * 已存在待确认请求，且本次提交内容不同（或原请求不可读）。
 * 必须先由用户处理原 pending，不能静默覆盖或新建订单。
 */
class OrderPendingConflictException(
    val pending: PendingOrderCreate,
    message: String = "上一笔预订结果待确认，请先确认或放弃后再提交新订单"
) : Exception(message)
