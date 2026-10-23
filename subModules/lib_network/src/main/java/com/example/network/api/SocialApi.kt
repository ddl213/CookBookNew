package com.example.network.api

import com.example.network.bean.CollectionStateDto
import com.example.network.bean.CommentCreateBody
import com.example.network.bean.CommentDto
import com.example.network.bean.PageResponse
import com.example.network.bean.ShareBody
import com.example.network.bean.ShareResultDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CookBookService 社交接口：评论、收藏与菜谱分享。
 *
 * baseUrl 已包含 /api/v1/，接口使用相对路径；全部需要登录，
 * 菜谱可见性由后端校验，无权访问统一返回 404。
 */
interface SocialApi {

    @GET("recipes/{id}/comments")
    suspend fun listComments(
        @Path("id") recipeId: Long,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): PageResponse<CommentDto>

    @POST("recipes/{id}/comments")
    suspend fun createComment(
        @Path("id") recipeId: Long,
        @Body body: CommentCreateBody
    ): CommentDto

    @PUT("recipes/{id}/collection")
    suspend fun addCollection(@Path("id") recipeId: Long): CollectionStateDto

    @DELETE("recipes/{id}/collection")
    suspend fun removeCollection(@Path("id") recipeId: Long): CollectionStateDto

    @POST("recipes/{id}/share")
    suspend fun share(
        @Path("id") recipeId: Long,
        @Body body: ShareBody
    ): ShareResultDto
}
