package com.example.network.api

import com.example.network.bean.FriendDto
import com.example.network.bean.FriendRequestCreateBody
import com.example.network.bean.FriendRequestDto
import com.example.network.bean.PageResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CookBookService 好友接口。
 *
 * 删除好友返回 204 空响应，因此使用 Response<Unit> 避免 Retrofit 解析空 body。
 */
interface FriendApi {

    @GET("friends")
    suspend fun listFriends(
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): PageResponse<FriendDto>

    @POST("friends/requests")
    suspend fun sendRequest(@Body body: FriendRequestCreateBody): FriendRequestDto

    @GET("friends/requests")
    suspend fun listRequests(
        @Query("direction") direction: String,
        @Query("status") status: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): PageResponse<FriendRequestDto>

    @POST("friends/requests/{id}/accept")
    suspend fun acceptRequest(@Path("id") requestId: Long): FriendRequestDto

    @POST("friends/requests/{id}/reject")
    suspend fun rejectRequest(@Path("id") requestId: Long): FriendRequestDto

    @DELETE("friends/{userId}")
    suspend fun deleteFriend(@Path("userId") userId: Long): Response<Unit>
}
