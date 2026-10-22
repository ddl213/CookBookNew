package com.example.network.api

import com.example.network.bean.AuthResponse
import com.example.network.bean.LoginRequest
import com.example.network.bean.RegisterRequest
import com.example.network.bean.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * CookBookService 认证接口。
 * baseUrl 已包含 /api/v1/，因此这里使用相对路径。
 */
interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @GET("auth/me")
    suspend fun me(): UserDto
}
