package com.rhys.chat.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 生产环境聊天加密实现：密钥保存在 Android Keystore，算法 AES-256-GCM。
 *
 * - 密钥别名固定（默认 `cookbook_chat_v1`），首次加密时生成，之后复用。
 * - 密钥不可用（如被系统清除）时加密抛 [ChatCipherException]，发送/写入明确失败；
 *   解密返回 [ChatDecryption.Unavailable]，由 UI 展示安全占位。
 * - KeyStore 不可用（例如缺少 AndroidKeyStore 的 JVM 单测）同样不会回退明文。
 */
class KeystoreChatCipher(
    private val alias: String = DEFAULT_ALIAS
) : ChatCipher {

    private val delegate: AesGcmChatCipher by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AesGcmChatCipher { loadOrCreateKey() }
    }

    @Volatile
    private var cachedKey: SecretKey? = null

    override fun encrypt(plaintext: String): String = delegate.encrypt(plaintext)

    override fun decrypt(encoded: String): ChatDecryption = delegate.decrypt(encoded)

    /**
     * 首次并发加密只能生成一把密钥：进程级锁 + double-check，
     * 避免后生成的密钥覆盖先生成的密钥，导致旧密文永久不可解。
     */
    private fun loadOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        return synchronized(KEY_LOCK) {
            cachedKey ?: try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
                    ?: generateKey()
            } catch (e: Exception) {
                throw ChatCipherException("聊天加密密钥不可用", e)
            }.also { cachedKey = it }
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        /** 跨实例共享，保证同一进程内并发首次访问只创建一把 Keystore 密钥。 */
        private val KEY_LOCK = Any()

        /** 全局唯一的聊天缓存密钥别名；变更会导致旧密文不可解密。 */
        const val DEFAULT_ALIAS = "cookbook_chat_v1"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SIZE_BITS = 256
    }
}
