package com.rhys.relation.model

/**
 * 好友申请状态；后端 status 字段为 pending / accepted / rejected。
 * 未识别的状态归为 UNKNOWN，避免把未知状态误判为可操作。
 */
enum class FriendRequestStatus(val raw: String) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    REJECTED("rejected"),
    UNKNOWN("");

    val isPending: Boolean
        get() = this == PENDING

    companion object {
        fun fromRaw(raw: String?): FriendRequestStatus {
            return entries.firstOrNull {
                it != UNKNOWN && it.raw.equals(raw, ignoreCase = true)
            } ?: UNKNOWN
        }
    }
}
