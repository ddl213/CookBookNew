package com.example.network.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * 资料更新参数的客户端防御性校验。
 *
 * 归一化是纯函数，不访问 ApiClient 或网络，可直接断言结果与异常。
 */
class UserProfileUpdateValidationTest {

    @Test
    fun `normalizedProfile trims provided fields`() {
        val body = UserRepository.normalizedProfile(
            nickname = "  小厨  ",
            avatar = "  /uploads/a.png  ",
            bio = "  喜欢做饭  "
        )
        assertEquals("小厨", body.nickname)
        assertEquals("/uploads/a.png", body.avatar)
        assertEquals("喜欢做饭", body.bio)
    }

    @Test
    fun `normalizedProfile rejects blank nickname`() {
        assertInvalidArgument { UserRepository.normalizedProfile(nickname = "") }
        assertInvalidArgument { UserRepository.normalizedProfile(nickname = "   ") }
    }

    @Test
    fun `normalizedProfile rejects nickname longer than limit`() {
        val tooLong = "昵".repeat(UserRepository.MAX_NICKNAME_LENGTH + 1)
        assertInvalidArgument { UserRepository.normalizedProfile(nickname = tooLong) }
    }

    @Test
    fun `normalizedProfile accepts nickname at limit`() {
        val atLimit = "昵".repeat(UserRepository.MAX_NICKNAME_LENGTH)
        assertEquals(atLimit, UserRepository.normalizedProfile(nickname = atLimit).nickname)
    }

    @Test
    fun `normalizedProfile rejects bio longer than limit`() {
        val tooLong = "生".repeat(UserRepository.MAX_BIO_LENGTH + 1)
        assertInvalidArgument { UserRepository.normalizedProfile(bio = tooLong) }
    }

    @Test
    fun `normalizedProfile allows empty bio to clear`() {
        val body = UserRepository.normalizedProfile(bio = "")
        assertEquals("", body.bio)
        assertNull(body.nickname)
        assertNull(body.avatar)

        val blankBody = UserRepository.normalizedProfile(bio = "   ")
        assertEquals("", blankBody.bio)
    }

    @Test
    fun `normalizedProfile rejects blank avatar`() {
        assertInvalidArgument { UserRepository.normalizedProfile(avatar = "") }
        assertInvalidArgument { UserRepository.normalizedProfile(avatar = "   ") }
    }

    @Test
    fun `normalizedProfile rejects when all fields are null`() {
        assertInvalidArgument { UserRepository.normalizedProfile() }
        assertInvalidArgument {
            UserRepository.normalizedProfile(nickname = null, avatar = null, bio = null)
        }
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
