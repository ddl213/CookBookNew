package com.rhys.cookbook.form

/**
 * 菜谱表单的固定选项与后端字符串映射。
 *
 * 分类固定三项，同时允许用户输入自定义分类；提交始终发送最终字符串。
 * visibility / status 的未知值不自行扩展业务含义，只回退到产品默认值。
 */
object RecipeFormOptions {

    val DEFAULT_CATEGORIES: List<String> = listOf("家常菜", "早餐", "甜品")

    const val VISIBILITY_FRIENDS = "friends"
    const val VISIBILITY_PUBLIC = "public"

    const val STATUS_DRAFT = "draft"
    const val STATUS_PUBLISHED = "published"

    fun visibilityOf(raw: String?): String =
        if (raw?.trim()?.lowercase() == VISIBILITY_PUBLIC) VISIBILITY_PUBLIC else VISIBILITY_FRIENDS

    fun statusOf(raw: String?): String =
        if (raw?.trim()?.lowercase() == STATUS_DRAFT) STATUS_DRAFT else STATUS_PUBLISHED
}
