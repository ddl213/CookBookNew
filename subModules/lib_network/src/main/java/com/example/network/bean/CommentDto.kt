package com.example.network.bean

/**
 * 菜谱评论，对应 /recipes/:id/comments。
 *
 * author 是评论者简要资料；createdAt 为后端 ISO 时间字符串。
 */
data class CommentDto(
    val id: Long = 0L,
    val recipeId: Long = 0L,
    val content: String = "",
    val author: UserSummaryDto = UserSummaryDto(),
    val createdAt: String? = null
)

/** 发表评论请求体：{ content }，后端 trim 后要求 1..1000 字。 */
data class CommentCreateBody(
    val content: String
)
