package com.example.network

/**
 * 认证相关请求的可展示异常。
 * message 已转换为用户可读文案，不包含堆栈或服务端内部细节。
 */
class AuthException(
    val code: String,
    message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause) {

    val isUnauthorized: Boolean
        get() = httpStatus == 401
}
