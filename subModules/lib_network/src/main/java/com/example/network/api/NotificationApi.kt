package com.example.network.api

import com.example.network.bean.ChatNotificationDto
import com.example.network.bean.CursorPage
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 消息列表中的独立通知接口。
 *
 * 本期只有预订通知（type=RESERVATION），无未读计数与已读回执。
 * baseUrl 已包含 /api/v1/，接口使用相对路径；需要登录。
 */
interface NotificationApi {

    @GET("notifications")
    suspend fun listNotifications(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?
    ): CursorPage<ChatNotificationDto>
}
