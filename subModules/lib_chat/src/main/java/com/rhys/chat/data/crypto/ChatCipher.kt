package com.rhys.chat.data.crypto

/**
 * 聊天本地缓存字段加密。
 *
 * 约束：
 * - 只加密用户可见文本（消息正文、昵称、头像、会话预览、通知标题/正文）；
 *   ID、时间、类型、状态保持明文以便查询与排序。
 * - 解密失败（密钥失效、数据损坏、版本不支持）不得崩溃，也不得回退明文，
 *   返回 [ChatDecryption.Unavailable] 由调用方展示安全占位。
 * - 加密失败必须明确抛出 [ChatCipherException]，禁止把明文写入数据库。
 */
interface ChatCipher {

    /** 加密非空文本；失败时抛 [ChatCipherException]，不返回明文。 */
    fun encrypt(plaintext: String): String

    /** 解密；任何失败都返回 [ChatDecryption.Unavailable]。 */
    fun decrypt(encoded: String): ChatDecryption

    companion object {
        /** 解密失败时允许 UI 展示的唯一占位文案。 */
        const val UNAVAILABLE_TEXT = "[内容不可用]"
    }
}

sealed interface ChatDecryption {
    data class Success(val value: String) : ChatDecryption

    /** 密钥失效或密文损坏；调用方必须使用安全占位，不得展示原文。 */
    object Unavailable : ChatDecryption
}

/** 加密不可用（密钥生成/取用失败等）；message 可展示且不包含明文。 */
class ChatCipherException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
