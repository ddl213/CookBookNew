package com.example.network.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 公开菜谱/好友动态接口的查询参数归一化。
 *
 * 归一化发生在访问 ApiClient 之前，纯 JVM 即可覆盖，不需要 Mockito 或真实网络。
 */
class RecipeRepositoryPublicFeedTest {

    @Test
    fun `null filter stays null`() {
        assertNull(RecipeRepository.normalizedFilter(null))
    }

    @Test
    fun `blank filter becomes null`() {
        assertNull(RecipeRepository.normalizedFilter(""))
        assertNull(RecipeRepository.normalizedFilter("   "))
        assertNull(RecipeRepository.normalizedFilter("\t\n"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("家常菜", RecipeRepository.normalizedFilter("  家常菜  "))
        assertEquals("番茄", RecipeRepository.normalizedFilter("\t番茄\n"))
        assertEquals("breakfast", RecipeRepository.normalizedFilter(" breakfast "))
    }

    @Test
    fun `normal value is kept unchanged`() {
        assertEquals("甜品", RecipeRepository.normalizedFilter("甜品"))
        assertEquals("红烧肉", RecipeRepository.normalizedFilter("红烧肉"))
    }
}
