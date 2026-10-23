package com.example.network.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * 社交接口的客户端防御性校验。
 *
 * 校验发生在访问 ApiClient 之前，因此不需要 Mockito 或真实网络即可断言异常。
 */
class RecipeRepositorySocialValidationTest {

    private val repository = RecipeRepository()

    @Test
    fun `listComments rejects non positive recipe id`() {
        assertInvalidArgument { runBlocking { repository.listComments(0) } }
        assertInvalidArgument { runBlocking { repository.listComments(-1) } }
    }

    @Test
    fun `createComment rejects blank content`() {
        assertInvalidArgument { runBlocking { repository.createComment(1, "   ") } }
        assertInvalidArgument { runBlocking { repository.createComment(1, "") } }
    }

    @Test
    fun `createComment rejects content longer than limit`() {
        val tooLong = "评".repeat(RecipeRepository.MAX_COMMENT_LENGTH + 1)
        assertInvalidArgument { runBlocking { repository.createComment(1, tooLong) } }
    }

    @Test
    fun `setCollected rejects non positive recipe id`() {
        assertInvalidArgument { runBlocking { repository.setCollected(0, true) } }
    }

    @Test
    fun `share rejects non positive ids`() {
        assertInvalidArgument { runBlocking { repository.share(0, 1) } }
        assertInvalidArgument { runBlocking { repository.share(1, 0) } }
    }

    @Test
    fun `page parameters are normalized before request`() {
        assertEquals(1, RecipeRepository.normalizedPage(0))
        assertEquals(1, RecipeRepository.normalizedPage(-5))
        assertEquals(3, RecipeRepository.normalizedPage(3))

        assertEquals(1, RecipeRepository.normalizedPageSize(0))
        assertEquals(1, RecipeRepository.normalizedPageSize(-20))
        assertEquals(20, RecipeRepository.normalizedPageSize(20))
        assertEquals(50, RecipeRepository.normalizedPageSize(51))
        assertEquals(50, RecipeRepository.normalizedPageSize(500))
    }

    private inline fun assertInvalidArgument(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
