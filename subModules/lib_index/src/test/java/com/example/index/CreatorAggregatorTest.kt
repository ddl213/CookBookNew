package com.example.index

import com.example.network.bean.RecipeDto
import com.example.network.bean.UserSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatorAggregatorTest {

    @Test
    fun `keeps first appearance order`() {
        val recipes = listOf(
            recipe(author(3, "丙")),
            recipe(author(1, "甲")),
            recipe(author(2, "乙"))
        )

        val creators = CreatorAggregator.collect(recipes)

        assertEquals(listOf(3L, 1L, 2L), creators.map { it.id })
        assertEquals(listOf("丙", "甲", "乙"), creators.map { it.displayName })
    }

    @Test
    fun `repeated author is kept once at first position`() {
        val first = author(7, "小厨")
        val recipes = listOf(
            recipe(first),
            recipe(author(8, "阿甜")),
            recipe(first.copy(nickname = "改名后的小厨"))
        )

        val creators = CreatorAggregator.collect(recipes)

        assertEquals(2, creators.size)
        assertEquals(7L, creators[0].id)
        assertEquals("小厨", creators[0].displayName)
        assertEquals(8L, creators[1].id)
    }

    @Test
    fun `skips null and invalid authors`() {
        val recipes = listOf(
            recipe(null),
            recipe(author(0, "零号")),
            recipe(author(-1, "负号")),
            recipe(author(5, "有效作者"))
        )

        val creators = CreatorAggregator.collect(recipes)

        assertEquals(listOf(5L), creators.map { it.id })
    }

    @Test
    fun `empty input returns empty list`() {
        assertTrue(CreatorAggregator.collect(emptyList()).isEmpty())
    }

    private fun recipe(author: UserSummaryDto?): RecipeDto =
        RecipeDto(id = 1L, name = "测试菜谱", author = author)

    private fun author(id: Long, nickname: String): UserSummaryDto =
        UserSummaryDto(id = id, username = "user$id", nickname = nickname)
}
