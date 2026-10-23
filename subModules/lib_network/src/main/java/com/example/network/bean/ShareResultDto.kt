package com.example.network.bean

/** 分享菜谱请求体：{ toUserId }。 */
data class ShareBody(
    val toUserId: Long
)

/** 分享结果，对应 POST /recipes/:id/share。 */
data class ShareResultDto(
    val shared: Boolean = false,
    val notificationId: Long = 0L
)
