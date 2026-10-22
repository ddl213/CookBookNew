package com.example.network.bean

/**
 * 注册 / 登录成功响应：{ token, user }。
 */
data class AuthResponse(
    val token: String,
    val user: UserDto
)
