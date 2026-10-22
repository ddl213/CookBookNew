package com.example.network.bean

/**
 * 菜谱作者、好友申请发起人/接收人等场景的简要用户信息。
 * avatar 可能为空，展示时需回退到首字占位。
 */
data class UserSummaryDto(
    val id: Long = 0L,
    val username: String = "",
    val nickname: String = "",
    val avatar: String? = null
) {
    val displayName: String
        get() = nickname.ifBlank { username }
}
