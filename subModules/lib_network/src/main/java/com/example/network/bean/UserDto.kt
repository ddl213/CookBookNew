package com.example.network.bean

/**
 * 后端公开用户信息，对应 CookBookService 的 toPublicUser 输出。
 * avatar、bio、createdAt 均可能为空。
 */
data class UserDto(
    val id: Long,
    val username: String,
    val nickname: String,
    val avatar: String? = null,
    val bio: String? = null,
    val createdAt: String? = null
) {
    /** 展示用名称：昵称为空时回退到用户名。 */
    val displayName: String
        get() = nickname.orEmpty().ifBlank { username }
}
