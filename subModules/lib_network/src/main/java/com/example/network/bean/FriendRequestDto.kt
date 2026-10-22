package com.example.network.bean

/**
 * 好友申请，对应 /friends/requests 系列接口。
 *
 * status 取值：pending / accepted / rejected。
 */
data class FriendRequestDto(
    val id: Long = 0L,
    val status: String = "",
    val message: String? = null,
    val from: UserSummaryDto = UserSummaryDto(),
    val to: UserSummaryDto = UserSummaryDto(),
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/** 发送好友申请请求体：{ toUserId, message? }。 */
data class FriendRequestCreateBody(
    val toUserId: Long,
    val message: String? = null
)
