package com.rhys.chat.data.crypto

import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

/** AES-256-GCM 字段加密：随机 IV、可逆、篡改/密钥失效安全降级。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AesGcmChatCipherTest {

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmChatCipher { key }

    @Test
    fun `same plaintext encrypts with different iv and round trips`() {
        val first = cipher.encrypt("同一段明文")
        val second = cipher.encrypt("同一段明文")

        assertNotEquals(first, second)
        val firstParts = first.split(":")
        val secondParts = second.split(":")
        assertEquals("v1", firstParts[0])
        assertEquals(3, firstParts.size)
        assertNotEquals(firstParts[1], secondParts[1])
        assertEquals("同一段明文", decrypt(first))
        assertEquals("同一段明文", decrypt(second))
    }

    @Test
    fun `tampered ciphertext is unavailable and never returns plaintext`() {
        val encoded = cipher.encrypt("不可篡改")
        val parts = encoded.split(":")
        val payload = Base64.decode(parts[2], Base64.NO_WRAP)
        payload[payload.size - 1] = (payload[payload.size - 1].toInt() xor 0xFF).toByte()
        val tampered = "${parts[0]}:${parts[1]}:${Base64.encodeToString(payload, Base64.NO_WRAP)}"

        assertSame(ChatDecryption.Unavailable, cipher.decrypt(tampered))
        assertSame(ChatDecryption.Unavailable, cipher.decrypt("not-a-cipher"))
        assertSame(ChatDecryption.Unavailable, cipher.decrypt("v2:a:b"))
    }

    @Test
    fun `empty plaintext round trips and too short payload stays unavailable`() {
        val encoded = cipher.encrypt("")

        assertEquals("", decrypt(encoded))
        // 3 字节 payload 短于 128-bit tag，必须判定为损坏。
        assertSame(ChatDecryption.Unavailable, cipher.decrypt("v1:AAAA:BBBB"))
        assertSame(ChatDecryption.Unavailable, cipher.decrypt("v1:AAAA:"))
    }

    @Test
    fun `unavailable key fails encryption explicitly and decryption safely`() {
        val broken = AesGcmChatCipher { throw IllegalStateException("keystore unavailable") }
        try {
            broken.encrypt("不应落盘")
            fail("expected ChatCipherException")
        } catch (e: ChatCipherException) {
            assertTrue(e.message.orEmpty().isNotBlank())
            assertTrue(!e.message.orEmpty().contains("不应落盘"))
        }
        assertSame(ChatDecryption.Unavailable, broken.decrypt("v1:AAAA:BBBB"))
    }

    @Test
    fun `different keys cannot decrypt each other`() {
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val other = AesGcmChatCipher { otherKey }
        val encoded = cipher.encrypt("账号隔离")

        assertSame(ChatDecryption.Unavailable, other.decrypt(encoded))
        assertEquals("账号隔离", decrypt(encoded))
    }

    private fun decrypt(encoded: String): String =
        when (val result = cipher.decrypt(encoded)) {
            is ChatDecryption.Success -> result.value
            ChatDecryption.Unavailable -> throw AssertionError("expected decryptable ciphertext")
        }
}
