package com.rhys.order.data.pending

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 生产默认实现：Android Keystore 保存 AES-256 密钥。
 *
 * - 别名固定（默认 `cookbook_order_pending_v1`），首次使用时生成并复用。
 * - 密钥不可用（被系统清除、Keystore 缺失）时加密抛 [OrderPendingCipherException]，
 *   明确失败且不落明文；解密返回 [OrderPendingDecryption.Unavailable]。
 * - 纯 JVM 单测使用 [AesGcmOrderPendingCipher] 注入 JVM 密钥，不加载本类。
 */
class KeystoreOrderPendingCipher(
    private val alias: String = DEFAULT_ALIAS
) : OrderPendingCipher {

    private val delegate: AesGcmOrderPendingCipher by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AesGcmOrderPendingCipher { loadOrCreateKey() }
    }

    @Volatile
    private var cachedKey: SecretKey? = null

    override fun encrypt(plaintext: String): String = delegate.encrypt(plaintext)

    override fun decrypt(encoded: String): OrderPendingDecryption = delegate.decrypt(encoded)

    /**
     * 进程级锁 + double-check：并发首次访问只生成一把密钥，
     * 避免后生成的密钥覆盖旧密钥导致已落盘密文永久不可解。
     */
    private fun loadOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        return synchronized(KEY_LOCK) {
            cachedKey ?: try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
                    ?: generateKey()
            } catch (e: Exception) {
                throw OrderPendingCipherException("订单待确认请求加密密钥不可用", e)
            }.also { cachedKey = it }
        }
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
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

        /** 全局唯一的订单待确认请求密钥别名；变更会导致旧密文不可解密。 */
        const val DEFAULT_ALIAS = "cookbook_order_pending_v1"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_SIZE_BITS = 256
    }
}
