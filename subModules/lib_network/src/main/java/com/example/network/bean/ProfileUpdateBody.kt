package com.example.network.bean

/**
 * 更新当前用户资料请求体，对应 CookBookService 的 PATCH /users/me。
 *
 * 为 null 的字段由 Gson 默认省略，未修改的字段不会随请求发送；
 * 后端要求至少提交一个字段。
 */
data class ProfileUpdateBody(
    val nickname: String? = null,
    val avatar: String? = null,
    val bio: String? = null
)
