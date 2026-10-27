package com.example.network.api

import com.example.network.bean.PageResponse
import com.example.network.bean.ProfileUpdateBody
import com.example.network.bean.RecipeDto
import com.example.network.bean.UserDto
import com.example.network.bean.UserListItemDto
import com.example.network.bean.UserProfileDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CookBookService 用户接口。
 * baseUrl 已包含 /api/v1/，接口使用相对路径。
 */
interface UserApi {

    @GET("users/{id}")
    suspend fun profile(@Path("id") userId: Long): UserProfileDto

    @GET("users")
    suspend fun search(
        @Query("q") q: String?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): PageResponse<UserListItemDto>

    @GET("users/{id}/recipes")
    suspend fun recipes(
        @Path("id") userId: Long,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): PageResponse<RecipeDto>

    @PATCH("users/me")
    suspend fun updateProfile(@Body body: ProfileUpdateBody): UserDto
}
