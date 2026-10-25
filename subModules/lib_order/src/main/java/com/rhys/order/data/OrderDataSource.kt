package com.rhys.order.data

import com.example.network.AuthException
import com.example.network.bean.CreateOrderItem
import com.example.network.bean.OrderAction
import com.example.network.bean.OrderDto
import com.example.network.bean.PageResponse
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import java.util.Locale

/**
 * 订单可替换数据源边界。
 *
 * 页面与 [OrderRepository] 只依赖本接口，不直接依赖 Retrofit 或模拟数据；
 * 好友关系、参与方、动作权限、版本与幂等最终由服务端判定，客户端预校验不能替代权限控制。
 */
interface OrderDataSource {

    /** 是否为本地演示数据源；UI 必须据此明确标识演示模式。 */
    val isMock: Boolean

    /**
     * 创建订单。[demoContext] 仅 Mock 使用，Remote 必须忽略；
     * [idempotencyKey] 由调用方持久化并在重试时保持原值。
     */
    suspend fun createOrder(
        items: List<CreateOrderItem>,
        note: String?,
        idempotencyKey: String,
        demoContext: OrderDemoContext? = null
    ): OrderDto

    /** 订单分页。role 默认 buyer；status 筛选整单汇总状态；pageSize 归一化到 1..50。 */
    suspend fun listOrders(
        role: String = ROLE_BUYER,
        status: String? = null,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PageResponse<OrderDto>

    suspend fun getOrder(orderId: Long): OrderDto

    /** 明细动作；expectedVersion 为明细版本，冲突原样上抛。 */
    suspend fun actOnItem(
        orderId: Long,
        itemId: Long,
        action: String,
        expectedVersion: Long
    ): OrderDto

    /** 整单取消；expectedVersion 为订单版本，仅全部待接受时可原子成功。 */
    suspend fun cancelOrder(orderId: Long, expectedVersion: Long): OrderDto

    companion object {
        const val ROLE_BUYER = "buyer"
        const val ROLE_SELLER = "seller"

        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
        const val MAX_NOTE_LENGTH = 500
        const val MAX_IDEMPOTENCY_KEY_LENGTH = 200

        /** 分页页码最小为 1；超大页码由服务端返回空页。 */
        fun normalizedPage(page: Int): Int = page.coerceAtLeast(1)

        /** 分页大小归一化到 1..50，避免客户端传 0、负数或超大值。 */
        fun normalizedPageSize(pageSize: Int): Int = pageSize.coerceIn(1, MAX_PAGE_SIZE)

        /** 角色空白按 buyer；其他非法值视为调用方错误。 */
        fun normalizedRole(role: String?): String {
            val safe = role?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (safe.isEmpty()) return ROLE_BUYER
            require(safe == ROLE_BUYER || safe == ROLE_SELLER) {
                "role must be buyer or seller"
            }
            return safe
        }

        /** 状态筛选 trim 后统一为大写；空白返回 null。未知值不做本地扩展，交给服务端/空结果。 */
        fun normalizedStatus(status: String?): String? =
            status?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

        /** 幂等键 trim 后必须为 1..200 字符。 */
        fun normalizedIdempotencyKey(idempotencyKey: String): String {
            val safe = idempotencyKey.trim()
            require(safe.isNotEmpty()) { "idempotencyKey must not be blank" }
            require(safe.length <= MAX_IDEMPOTENCY_KEY_LENGTH) {
                "idempotencyKey must not exceed $MAX_IDEMPOTENCY_KEY_LENGTH characters"
            }
            return safe
        }

        /** 明细动作仅 accept/reject/ready/cancel/complete；大小写不敏感。 */
        fun normalizedItemAction(action: String): String {
            val safe = action.trim().lowercase(Locale.ROOT)
            require(safe in OrderAction.ITEM_ACTIONS) {
                "unsupported order item action: $action"
            }
            return safe
        }

        /**
         * 合并重复菜品并按 recipeId 排序，数量正数且合并后不溢出。
         * 这是客户端预校验；服务端仍会再次合并与校验。
         */
        fun normalizedItems(items: List<CreateOrderItem>): List<CreateOrderItem> {
            if (items.isEmpty()) throw validationError("请至少选择一道菜")
            val merged = LinkedHashMap<Long, Long>()
            items.forEach { item ->
                if (item.recipeId <= 0L) throw validationError("菜谱参数不合法")
                if (item.quantity <= 0) throw validationError("菜品数量必须为正整数")
                val total = (merged[item.recipeId] ?: 0L) + item.quantity.toLong()
                if (total > Int.MAX_VALUE.toLong()) {
                    throw validationError("菜品数量过大")
                }
                merged[item.recipeId] = total
            }
            return merged.entries
                .sortedBy { it.key }
                .map { CreateOrderItem(recipeId = it.key, quantity = it.value.toInt()) }
        }

        /** 备注 trim；空白视为 null；超过 500 字拒绝。 */
        fun normalizedNote(note: String?): String? {
            val safe = note?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (safe.length > MAX_NOTE_LENGTH) {
                throw validationError("订单备注不能超过 $MAX_NOTE_LENGTH 字")
            }
            return safe
        }

        /** 一次创建请求的规范化结果；[fingerprint] 用于幂等同内容判断。 */
        fun normalizedCreate(items: List<CreateOrderItem>, note: String?): NormalizedOrderCreate {
            val safeItems = normalizedItems(items)
            val safeNote = normalizedNote(note)
            val fingerprint = buildString {
                safeItems.forEach { append(it.recipeId).append('x').append(it.quantity).append('|') }
                append("note=").append(safeNote.orEmpty())
            }
            return NormalizedOrderCreate(
                items = safeItems,
                note = safeNote,
                fingerprint = fingerprint
            )
        }

        private fun validationError(message: String): AuthException =
            AuthException(
                code = "VALIDATION_ERROR",
                message = message,
                httpStatus = 400
            )
    }
}

/** 规范化后的创建请求。 */
data class NormalizedOrderCreate(
    val items: List<CreateOrderItem>,
    val note: String?,
    val fingerprint: String
)

/**
 * Mock 演示上下文，仅用于离线演示/自动化验收；Remote 数据源必须忽略。
 *
 * [recipes] 以 recipeId 为键，提供菜名、图片快照与提供方；不同 recipe 可指向不同
 * 提供方，用于验证 MIXED_SELLERS。`sellerId/sellerName/sellerUsername` 是默认提供方。
 * `accessible=false` 表示菜谱不可访问，Mock 会按 RECIPE_NOT_FOUND 拒绝。
 */
data class OrderDemoContext(
    val sellerId: Long,
    val sellerName: String,
    val sellerUsername: String = "seller_$sellerId",
    val isFriend: Boolean = true,
    val recipes: Map<Long, OrderDemoRecipe> = emptyMap()
)

data class OrderDemoRecipe(
    val name: String,
    val imageUrl: String = "",
    val sellerId: Long? = null,
    val sellerName: String? = null,
    val sellerUsername: String? = null,
    val accessible: Boolean = true
)

/** Mock 数据源解析出的当前登录用户。 */
data class OrderMockActor(
    val id: Long,
    val username: String,
    val nickname: String
)

fun interface OrderActorProvider {
    fun currentActor(): OrderMockActor?
}

/** 生产默认实现：只认已登录用户，Unknown/LoggedOut 返回 null。 */
object SessionOrderActorProvider : OrderActorProvider {
    override fun currentActor(): OrderMockActor? {
        val user = (SessionManager.state.value as? AuthState.LoggedIn)
            ?.user ?: return null
        return OrderMockActor(
            id = user.id,
            username = user.username,
            nickname = user.displayName
        )
    }
}

/** 订单待确认请求的 owner 来源；未登录返回 null，不得读写他人 pending。 */
fun interface OrderOwnerProvider {
    fun currentOwnerId(): Long?
}

/** 生产默认实现：从全局登录态读取当前用户 ID。 */
object SessionOrderOwnerProvider : OrderOwnerProvider {
    override fun currentOwnerId(): Long? =
        (SessionManager.state.value as? AuthState.LoggedIn)?.user?.id
}
