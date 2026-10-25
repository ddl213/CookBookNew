package com.rhys.order.data.pending

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM 待确认请求加密。
 *
 * 密文格式：`v1:<hex iv>:<hex ciphertext>`。使用十六进制而非 Android Base64，
 * 使同一实现可在纯 JVM 单测中注入 `KeyGenerator` 生成的密钥验证。
 * 生产密钥由 [KeystoreOrderPendingCipher] 从 Android Keystore 提供。
 */
class AesGcmOrderPendingCipher(
    private val keyProvider: () -> SecretKey
) : OrderPendingCipher {

    override fun encrypt(plaintext: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            buildString {
                append(VERSION)
                append(SEPARATOR)
                append(iv.toHex())
                append(SEPARATOR)
                append(encrypted.toHex())
            }
        } catch (e: Exception) {
            // 明确失败，绝不回退明文。
            throw OrderPendingCipherException("订单待确认请求加密失败", e)
        }
    }

    override fun decrypt(encoded: String): OrderPendingDecryption {
        return try {
            val parts = encoded.split(SEPARATOR)
            if (parts.size != 3 || parts[0] != VERSION) {
                return OrderPendingDecryption.Unavailable
            }
            val iv = parts[1].hexToBytes() ?: return OrderPendingDecryption.Unavailable
            val payload = parts[2].hexToBytes() ?: return OrderPendingDecryption.Unavailable
            // GCM 空明文密文正好是 TAG_BITS/8 字节 tag；短于 tag 才视为损坏。
            if (iv.isEmpty() || payload.size < TAG_BITS / 8) {
                return OrderPendingDecryption.Unavailable
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyProvider(),
                GCMParameterSpec(TAG_BITS, iv)
            )
            OrderPendingDecryption.Success(
                String(cipher.doFinal(payload), Charsets.UTF_8)
            )
        } catch (e: Exception) {
            // 密钥失效、数据损坏、格式不匹配都返回不可用，不崩溃也不回退明文。
            OrderPendingDecryption.Unavailable
        }
    }

    companion object {
        const val VERSION = "v1"
        private const val SEPARATOR = ":"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128

        private fun ByteArray.toHex(): String = buildString(size * 2) {
            this@toHex.forEach { byte ->
                append((byte.toInt() and 0xFF).toString(16).padStart(2, '0'))
            }
        }

        private fun String.hexToBytes(): ByteArray? {
            if (length % 2 != 0) return null
            return try {
                ByteArray(length / 2) { index ->
                    substring(index * 2, index * 2 + 2).toInt(16).toByte()
                }
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}
