package com.example.network.bean

/**
 * 创建 / 更新菜谱的请求体，字段与 CookBookService 对齐。
 *
 * 可空字段保持 null 时由 Gson 省略，便于 PATCH 只提交表单实际持有的子集。
 * status 为 published / draft；缺省时后端按 published 处理。
 */
data class RecipeWriteBody(
    val name: String = "",
    val description: String? = null,
    val imageUrl: String? = null,
    val category: String = "",
    val visibility: String = "",
    val status: String? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList()
)
