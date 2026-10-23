package com.example.network.bean

/**
 * 菜谱接口返回模型，字段与 CookBookService serializeRecipe 对齐。
 *
 * visibility：friends / public；status：published / draft，旧响应缺省时按 published 兼容。
 * canReserve 由后端按好友关系计算。草稿可见性与预订资格均由后端判定，客户端只读取展示。
 */
data class RecipeDto(
    val id: Long = 0L,
    val name: String = "",
    val description: String? = null,
    val imageUrl: String? = null,
    val category: String? = null,
    val visibility: String? = null,
    val status: String = "published",
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val author: UserSummaryDto? = null,
    val collected: Boolean = false,
    val canReserve: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
