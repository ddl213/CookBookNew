package com.marky.route.compiler.utils

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


object EncryptUtils {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val KEY_SIZE = 128
    private const val IV_SIZE = 16

    fun encrypt(plainText: String, key: String): String {
        val iv = generateIv()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = generateSecretKey(key)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combinedBytes = iv + encryptedBytes

        return base64Encode(combinedBytes)
    }

    fun decrypt(encryptedText: String, key: String): String {
        val combinedBytes = base64Decode(encryptedText)
        val iv = combinedBytes.copyOfRange(0, IV_SIZE)
        val encryptedBytes = combinedBytes.copyOfRange(IV_SIZE, combinedBytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = generateSecretKey(key)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun generateIv(): ByteArray {
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        return iv
    }

    private fun generateSecretKey(key: String): SecretKeySpec {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val sha = java.security.MessageDigest.getInstance("SHA-256")
        val keyDigest = sha.digest(keyBytes)
        return SecretKeySpec(keyDigest, 0, KEY_SIZE / 8, ALGORITHM)
    }

    private fun base64Encode(input: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = StringBuilder()
        var padding = 0
        var buffer = 0
        var bitsLeft = 0

        for (b in input) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 6) {
                bitsLeft -= 6
                output.append(table[(buffer shr bitsLeft) and 0x3F])
            }
        }

        if (bitsLeft > 0) {
            buffer = buffer shl (6 - bitsLeft)
            output.append(table[buffer and 0x3F])
            padding = (3 - (input.size % 3)) % 3
        }

        repeat(padding) { output.append('=') }

        return output.toString()
    }

    private fun base64Decode(input: String): ByteArray {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (c in input) {
            if (c == '=') break
            val value = table.indexOf(c)
            if (value >= 0) {
                buffer = (buffer shl 6) or value
                bitsLeft += 6
                if (bitsLeft >= 8) {
                    bitsLeft -= 8
                    output.add(((buffer shr bitsLeft) and 0xFF).toByte())
                }
            }
        }

        return output.toByteArray()
    }
}