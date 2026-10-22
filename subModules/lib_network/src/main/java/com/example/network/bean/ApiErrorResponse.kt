package com.example.network.bean

/**
 * 后端统一错误结构：{ error: { code, message, details? } }。
 */
data class ApiErrorResponse(
    val error: ApiError? = null
)

data class ApiError(
    val code: String? = null,
    val message: String? = null,
    val details: List<ApiErrorDetail>? = null
)

/**
 * 校验错误明细，主要出现在 400 VALIDATION_ERROR 中。
 */
data class ApiErrorDetail(
    val path: String? = null,
    val message: String? = null,
    val code: String? = null
)
