package com.example.network.bean

/**
 * 好友列表条目，对应 GET /friends 的 items。
 */
data class FriendDto(
    val id: Long = 0L,
    val username: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val bio: String? = null,
    val createdAt: String? = null,
    val friendSince: String? = null
) {
    val displayName: String
        get() = nickname.ifBlank { username }
}
