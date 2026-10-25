package com.rhys.order.data.pending

/**
 * 待确认订单请求的字段加密。
 *
 * 只加密请求内容（明细与备注）；幂等键以明文元数据保存，便于定位和丢弃。
 * 加密失败必须抛 [OrderPendingCipherException]，禁止回退明文；
 * 解密失败返回 [OrderPendingDecryption.Unavailable]，由仓储提示放弃而非猜测内容。
 */
interface OrderPendingCipher {

    fun encrypt(plaintext: String): String

    fun decrypt(encoded: String): OrderPendingDecryption
}

sealed interface OrderPendingDecryption {
    data class Success(val value: String) : OrderPendingDecryption

    /** 密钥失效、密文损坏或格式不支持；不得回退明文。 */
    object Unavailable : OrderPendingDecryption
}

/** 订单待确认请求加密不可用；message 可展示且不包含明文。 */
class OrderPendingCipherException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
