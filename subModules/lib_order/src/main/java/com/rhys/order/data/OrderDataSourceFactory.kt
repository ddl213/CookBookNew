package com.rhys.order.data

import com.android.common.utils.LogUtils
import com.example.network.order.OrderDataSourceConfig
import com.example.network.order.OrderDataSourceKind
import com.example.network.order.OrderDataSourceResolution

/**
 * 按共享 [OrderDataSourceConfig] 创建 Mock/Remote 订单数据源。
 *
 * 非法配置由 [OrderDataSourceConfig] 回退 REMOTE，本工厂负责记录 warning。
 * 工厂参数仅供测试替换；生产默认使用真实接口与进程内 Mock 存储。
 */
object OrderDataSourceFactory {

    private const val TAG = "OrderDataSourceFactory"

    fun create(
        resolution: OrderDataSourceResolution = OrderDataSourceConfig.current(),
        onWarning: (String) -> Unit = { message -> LogUtils.w(message, TAG) },
        mockFactory: () -> OrderDataSource = { MockOrderDataSource() },
        remoteFactory: () -> OrderDataSource = { RemoteOrderDataSource() }
    ): OrderDataSource {
        if (resolution.fallback) {
            onWarning(
                "未知的 orderDataSource='${resolution.rawValue}'，已回退 remote"
            )
        }
        return when (resolution.kind) {
            OrderDataSourceKind.MOCK -> mockFactory()
            OrderDataSourceKind.REMOTE -> remoteFactory()
        }
    }
}
