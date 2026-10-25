package com.rhys.chat.data.crypto

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM 聊天字段加密实现。
 *
 * 密文自包含版本号、随机 IV 与密文（含 128-bit tag）：
 * `v1:<base64 iv>:<base64 ciphertext>`。
 * 密钥由 [keyProvider] 提供：生产环境使用 Android Keystore（见 [KeystoreChatCipher]），
 * 测试可注入 JVM `KeyGenerator` 生成的 AES 密钥。
 */
class AesGcmChatCipher(
    private val keyProvider: () -> SecretKey
) : ChatCipher {

    override fun encrypt(plaintext: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            buildString {
                append(VERSION)
                append(SEPARATOR)
                append(Base64.encodeToString(iv, Base64.NO_WRAP))
                append(SEPARATOR)
                append(Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
        } catch (e: Exception) {
            // 明确失败，绝不回退明文。
            throw ChatCipherException("聊天内容加密失败", e)
        }
    }

    override fun decrypt(encoded: String): ChatDecryption {
        return try {
            val parts = encoded.split(SEPARATOR)
            if (parts.size != 3 || parts[0] != VERSION) return ChatDecryption.Unavailable
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val payload = Base64.decode(parts[2], Base64.NO_WRAP)
            // GCM 空明文密文正好是 TAG_BITS/8 字节 tag；短于 tag 才视为损坏。
            if (iv.isEmpty() || payload.size < TAG_BITS / 8) return ChatDecryption.Unavailable
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
            ChatDecryption.Success(String(cipher.doFinal(payload), Charsets.UTF_8))
        } catch (e: Exception) {
            // 密钥失效、数据损坏、格式不匹配都返回不可用，不崩溃也不回退明文。
            ChatDecryption.Unavailable
        }
    }

    companion object {
        const val VERSION = "v1"
        private const val SEPARATOR = ":"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
    }
}
