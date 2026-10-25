package com.example.network.order

import com.example.network.BuildConfig
import java.util.Locale

/**
 * 订单数据源类型。
 *
 * - [MOCK]：进程内演示数据，双方共享同一存储，必须由 UI 明确标识。
 * - [REMOTE]：真实后端；默认值，失败不得回退 Mock。
 */
enum class OrderDataSourceKind {
    MOCK,
    REMOTE
}

/**
 * 配置解析结果。
 *
 * [fallback] 为 true 表示输入不是合法数据源名称，已回退 [OrderDataSourceKind.REMOTE]。
 */
data class OrderDataSourceResolution(
    val kind: OrderDataSourceKind,
    val rawValue: String?,
    val fallback: Boolean
)

/**
 * 订单数据源共享配置（放在 `lib_network`，供 `lib_order` 与 P08-T05 通知适配共同读取）。
 *
 * 值来自 `BuildConfig.ORDER_DATA_SOURCE`，由 Gradle 属性 `orderDataSource` 在构建期写入：
 * **默认 remote（debug 与 release 相同）**，订单需要本地真实联调；
 * `-PorderDataSource=mock` 显式启用演示模式。非法值回退 remote，由工厂记录 warning。
 * 不使用 `chatDataSource` 作为订单默认值；服务地址统一复用 `NetworkConfig.baseUrl`。
 */
object OrderDataSourceConfig {

    const val SOURCE_MOCK = "mock"
    const val SOURCE_REMOTE = "remote"

    fun resolve(raw: String?): OrderDataSourceResolution {
        val normalized = raw?.trim()?.lowercase(Locale.ROOT)
        return when (normalized) {
            SOURCE_MOCK -> OrderDataSourceResolution(
                kind = OrderDataSourceKind.MOCK,
                rawValue = raw,
                fallback = false
            )
            SOURCE_REMOTE -> OrderDataSourceResolution(
                kind = OrderDataSourceKind.REMOTE,
                rawValue = raw,
                fallback = false
            )
            else -> OrderDataSourceResolution(
                kind = OrderDataSourceKind.REMOTE,
                rawValue = raw,
                fallback = true
            )
        }
    }

    /** 读取当前构建写入的数据源配置。 */
    fun current(): OrderDataSourceResolution = resolve(BuildConfig.ORDER_DATA_SOURCE)
}
