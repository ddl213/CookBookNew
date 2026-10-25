package com.example.network.api

import com.example.network.bean.CreateOrderBody
import com.example.network.bean.ExpectedVersionBody
import com.example.network.bean.OrderDto
import com.example.network.bean.PageResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 好友免费预订与双向订单接口。
 *
 * baseUrl 已包含 /api/v1/，接口使用相对路径；全部需要登录，
 * 参与方、动作权限与幂等最终由后端判定。
 */
interface OrderApi {

    /**
     * 创建订单。`Idempotency-Key` 必填（1～200 字符），同键同内容重放返回原订单，
     * 同键不同内容返回 409 IDEMPOTENCY_KEY_REUSED。
     */
    @POST("orders")
    suspend fun createOrder(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CreateOrderBody
    ): OrderDto

    /** 订单分页；role 为 buyer/seller，status 筛选整单汇总状态。 */
    @GET("orders")
    suspend fun listOrders(
        @Query("role") role: String,
        @Query("status") status: String?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): PageResponse<OrderDto>

    /** 订单详情；第三方访问返回 404。 */
    @GET("orders/{orderId}")
    suspend fun getOrder(
        @Path("orderId") orderId: Long
    ): OrderDto

    /** 明细动作；action 仅 accept/reject/ready/cancel/complete。 */
    @POST("orders/{orderId}/items/{itemId}/{action}")
    suspend fun actOnItem(
        @Path("orderId") orderId: Long,
        @Path("itemId") itemId: Long,
        @Path("action") action: String,
        @Body body: ExpectedVersionBody
    ): OrderDto

    /** 整单取消；仅当全部明细待接受时原子成功。 */
    @POST("orders/{orderId}/cancel")
    suspend fun cancelOrder(
        @Path("orderId") orderId: Long,
        @Body body: ExpectedVersionBody
    ): OrderDto
}
