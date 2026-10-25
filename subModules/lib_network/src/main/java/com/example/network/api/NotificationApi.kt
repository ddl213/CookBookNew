package com.example.network.api

import com.example.network.bean.ChatNotificationDto
import com.example.network.bean.CursorPage
import com.example.network.bean.OrderNotificationDto
import com.example.network.bean.PageResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 消息列表中的独立通知接口。
 *
 * 保留 P07 游标接口 `listNotifications` 供聊天通知继续使用；P08 订单通知使用
 * `category=order` 的页码分页接口。baseUrl 已包含 /api/v1/，需要登录。
 */
interface NotificationApi {

    /**
     * P07 遗留的游标分页通知接口（聊天通知）。
     *
     * 风险提示：当前后端 `GET /notifications` 只支持 `page/pageSize`，不识别
     * `limit/cursor`，因此本接口不会真正透传游标，返回的 `nextCursor` 为 null。
     *
     * P08 通知页使用 [listOrderNotifications]（`category=order` + 页码分页），
     * 本接口在 P08 main 中无调用方。若未来重新启用，必须在客户端内部把游标转换
     * 为页码后再请求，不得把 cursor 直接透传给后端；不得用于新功能。
     */
    @GET("notifications")
    suspend fun listNotifications(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?
    ): CursorPage<ChatNotificationDto>

    /**
     * 订单通知分页；`category=order` 只返回六类订单事件，分页结构与订单列表一致。
     * 调用方必须传入归一化后的 `page>=1`、`pageSize=1..50`。
     */
    @GET("notifications")
    suspend fun listOrderNotifications(
        @Query("category") category: String = "order",
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): PageResponse<OrderNotificationDto>
}
