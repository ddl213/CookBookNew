package com.example.network.bean

/**
 * 登录请求体，对应 POST /auth/login。
 */
data class LoginRequest(
    val username: String,
    val password: String
)
