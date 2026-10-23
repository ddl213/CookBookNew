package com.example.network.api

import com.example.network.bean.PageResponse
import com.example.network.bean.RecipeDto
import com.example.network.bean.RecipeWriteBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CookBookService 菜谱接口。
 * baseUrl 已包含 /api/v1/，接口使用相对路径。
 *
 * 删除返回 204 空响应，因此使用 Response<Unit>，调用方必须显式校验 2xx。
 */
interface RecipeApi {

    @GET("recipes/mine")
    suspend fun listMine(
        @Query("status") status: String,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int
    ): PageResponse<RecipeDto>

    @GET("recipes/{id}")
    suspend fun get(@Path("id") id: Long): RecipeDto

    @POST("recipes")
    suspend fun create(@Body body: RecipeWriteBody): RecipeDto

    @PATCH("recipes/{id}")
    suspend fun update(@Path("id") id: Long, @Body body: RecipeWriteBody): RecipeDto

    @DELETE("recipes/{id}")
    suspend fun delete(@Path("id") id: Long): Response<Unit>
}
