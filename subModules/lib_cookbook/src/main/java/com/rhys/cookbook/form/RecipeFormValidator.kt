package com.rhys.cookbook.form

/**
 * 表单输入快照；文本字段保持原始输入，由校验器统一 trim / 去空行。
 */
data class RecipeFormInput(
    val name: String = "",
    val category: String = "",
    val ingredientsText: String = "",
    val stepsText: String = ""
)

/**
 * 逐字段校验结果，便于表单就地提示而不只弹一条总错误。
 * [firstMessage] 返回第一个错误，供焦点定位或 Toast 兜底。
 */
data class RecipeFormErrors(
    val name: String? = null,
    val category: String? = null,
    val ingredients: String? = null,
    val steps: String? = null
) {
    val isValid: Boolean
        get() = name == null && category == null && ingredients == null && steps == null

    val firstMessage: String?
        get() = name ?: category ?: ingredients ?: steps
}

/**
 * 菜谱表单的纯 Kotlin 校验与解析，便于 JVM 单元测试。
 *
 * 必填规则对草稿与已发布一致：名称、分类、食材（≥1）、步骤（≥1）。
 * 简介、图片可选，不在此校验。
 */
object RecipeFormValidator {

    fun parseLines(text: String): List<String> {
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    fun validate(input: RecipeFormInput): RecipeFormErrors {
        val name = input.name.trim()
        val category = input.category.trim()
        val ingredients = parseLines(input.ingredientsText)
        val steps = parseLines(input.stepsText)

        return RecipeFormErrors(
            name = if (name.isEmpty()) "请输入菜谱名称" else null,
            category = if (category.isEmpty()) "请选择或输入分类" else null,
            ingredients = if (ingredients.isEmpty()) "请至少填写一种食材" else null,
            steps = if (steps.isEmpty()) "请至少填写一个步骤" else null
        )
    }
}
