package com.rhys.cookbook.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeFormValidatorTest {

    @Test
    fun `valid form requires name category ingredients and steps`() {
        val result = RecipeFormValidator.validate(
            RecipeFormInput(
                name = "番茄炒蛋",
                category = "家常菜",
                ingredientsText = "番茄 2 个\n鸡蛋 3 个",
                stepsText = "打蛋\n翻炒"
            )
        )
        assertTrue(result.isValid)
        assertNull(result.firstMessage)
    }

    @Test
    fun `blank name is rejected`() {
        val result = RecipeFormValidator.validate(validInput(name = "   "))
        assertFalse(result.isValid)
        assertEquals("请输入菜谱名称", result.name)
    }

    @Test
    fun `blank category is rejected`() {
        val result = RecipeFormValidator.validate(validInput(category = ""))
        assertFalse(result.isValid)
        assertEquals("请选择或输入分类", result.category)
    }

    @Test
    fun `whitespace only ingredients are rejected`() {
        val result = RecipeFormValidator.validate(validInput(ingredients = " \n\t\n "))
        assertFalse(result.isValid)
        assertEquals("请至少填写一种食材", result.ingredients)
    }

    @Test
    fun `whitespace only steps are rejected`() {
        val result = RecipeFormValidator.validate(validInput(steps = "\n  \n"))
        assertFalse(result.isValid)
        assertEquals("请至少填写一个步骤", result.steps)
    }

    @Test
    fun `first message follows field order`() {
        val result = RecipeFormValidator.validate(RecipeFormInput())
        assertEquals("请输入菜谱名称", result.firstMessage)
    }

    @Test
    fun `parse lines trims and drops empty lines`() {
        val parsed = RecipeFormValidator.parseLines("  番茄 2 个 \n\n\t鸡蛋 3 个\n   ")
        assertEquals(listOf("番茄 2 个", "鸡蛋 3 个"), parsed)
    }

    @Test
    fun `default categories are fixed three values`() {
        assertEquals(listOf("家常菜", "早餐", "甜品"), RecipeFormOptions.DEFAULT_CATEGORIES)
    }

    @Test
    fun `visibility falls back to friends for unknown values`() {
        assertEquals(RecipeFormOptions.VISIBILITY_PUBLIC, RecipeFormOptions.visibilityOf(" PUBLIC "))
        assertEquals(RecipeFormOptions.VISIBILITY_FRIENDS, RecipeFormOptions.visibilityOf("friends"))
        assertEquals(RecipeFormOptions.VISIBILITY_FRIENDS, RecipeFormOptions.visibilityOf(null))
    }

    @Test
    fun `status falls back to published for unknown values`() {
        assertEquals(RecipeFormOptions.STATUS_DRAFT, RecipeFormOptions.statusOf(" draft "))
        assertEquals(RecipeFormOptions.STATUS_PUBLISHED, RecipeFormOptions.statusOf("published"))
        assertEquals(RecipeFormOptions.STATUS_PUBLISHED, RecipeFormOptions.statusOf(null))
    }

    private fun validInput(
        name: String = "番茄炒蛋",
        category: String = "家常菜",
        ingredients: String = "番茄",
        steps: String = "翻炒"
    ) = RecipeFormInput(
        name = name,
        category = category,
        ingredientsText = ingredients,
        stepsText = steps
    )
}
