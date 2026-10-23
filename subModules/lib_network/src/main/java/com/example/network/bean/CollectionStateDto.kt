package com.example.network.bean

/** 收藏状态，对应 PUT/DELETE /recipes/:id/collection。 */
data class CollectionStateDto(
    val recipeId: Long = 0L,
    val collected: Boolean = false
)
