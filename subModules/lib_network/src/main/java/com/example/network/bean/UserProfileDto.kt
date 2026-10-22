package com.example.network.bean

/**
 * 用户主页数据，对应 GET /users/:id。
 *
 * friendCount / recipeCount 由后端按当前查看者权限计算；
 * isFriend 只表示双方当前好友关系，不代表可查看全部数据。
 */
data class UserProfileDto(
    val id: Long = 0L,
    val username: String = "",
    val nickname: String = "",
    val avatar: String? = null,
    val bio: String? = null,
    val createdAt: String? = null,
    val friendCount: Int = 0,
    val recipeCount: Int = 0,
    val isFriend: Boolean = false
) {
    val displayName: String
        get() = nickname.ifBlank { username }
}
