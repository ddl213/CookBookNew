package com.example.network.bean

/**
 * 菜谱接口返回模型，字段与 CookBookService serializeRecipe 对齐。
 *
 * visibility：friends / public；canReserve 由后端按好友关系计算。
 * 本模块只读取用于用户主页展示，不在客户端重新判定可见性。
 */
data class RecipeDto(
    val id: Long = 0L,
    val name: String = "",
    val description: String? = null,
    val imageUrl: String? = null,
    val category: String? = null,
    val visibility: String? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val author: UserSummaryDto? = null,
    val collected: Boolean = false,
    val canReserve: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
