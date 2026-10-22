package com.example.network.bean

/**
 * 用户搜索结果条目，对应 GET /users 的 items。
 */
data class UserListItemDto(
    val id: Long = 0L,
    val username: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val bio: String? = null,
    val createdAt: String? = null,
    val isFriend: Boolean = false
) {
    val displayName: String
        get() = nickname.ifBlank { username }
}
