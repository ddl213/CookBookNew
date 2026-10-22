package com.example.network.bean

/**
 * 注册请求体，对应 POST /auth/register。
 * nickname 可选，为空时由后端使用用户名作为默认昵称。
 */
data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String? = null
)
