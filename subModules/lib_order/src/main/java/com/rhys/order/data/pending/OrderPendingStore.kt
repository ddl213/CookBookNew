package com.rhys.order.data.pending

import com.android.common.utils.MMKVUtils
import com.example.network.bean.CreateOrderItem
import com.example.network.order.OrderDataSourceKind
import java.security.MessageDigest
import java.util.Locale

/**
 * 待确认创建请求的隔离维度。
 *
 * 同一 owner 在不同数据源（Mock/Remote）或不同后端环境（baseUrl）下
 * 不得串用 pending；key 由实现方按此三元组生成。
 */
data class OrderPendingScope(
    val ownerId: Long,
    val kind: OrderDataSourceKind,
    val baseUrl: String
)

/**
 * 一条待确认的创建请求。
 *
 * [readable] 为 false 表示密文损坏或密钥不可用：不得据此重放，
 * 只能提示用户放弃后重新提交。[idempotencyKey] 仍从明文元数据保留，
 * 便于 UI 展示/排查，但请求内容不可恢复。
 */
data class PendingOrderCreate(
    val idempotencyKey: String,
    val items: List<CreateOrderItem> = emptyList(),
    val note: String? = null,
    val createdAt: Long = 0L,
    val readable: Boolean = true
)

/**
 * 待确认请求的持久化边界。生产使用 MMKV，测试注入内存实现。
 * 存储内容只包含幂等键明文与加密后的规范化请求，禁止明文落盘备注/请求。
 */
interface OrderPendingStore {

    fun read(scope: OrderPendingScope): String?

    fun write(scope: OrderPendingScope, payload: String)

    fun clear(scope: OrderPendingScope)
}

/**
 * 生产实现：使用项目已有 MMKV。
 *
 * 存储键包含 ownerId、数据源类型与 baseUrl 摘要，保证账号、数据源、
 * 后端环境切换后不会读取或覆盖另一作用域的 pending。
 */
class MmkvOrderPendingStore : OrderPendingStore {

    override fun read(scope: OrderPendingScope): String? =
        MMKVUtils.getString(keyOf(scope), "")?.takeIf { it.isNotEmpty() }

    override fun write(scope: OrderPendingScope, payload: String) {
        MMKVUtils.setString(keyOf(scope), payload)
    }

    override fun clear(scope: OrderPendingScope) {
        MMKVUtils.removeValueForKey(keyOf(scope))
    }

    private fun keyOf(scope: OrderPendingScope): String {
        val kind = scope.kind.name.lowercase(Locale.ROOT)
        return "${KEY_PREFIX}_${scope.ownerId}_${kind}_${hash(scope.baseUrl)}"
    }

    /** baseUrl 可能含特殊字符；取 SHA-256 前 16 位作为安全、稳定的键片段。 */
    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return buildString {
            digest.forEach { byte ->
                append((byte.toInt() and 0xFF).toString(16).padStart(2, '0'))
            }
        }.take(16)
    }

    companion object {
        const val KEY_PREFIX = "order_pending_create_v1"
    }
}
