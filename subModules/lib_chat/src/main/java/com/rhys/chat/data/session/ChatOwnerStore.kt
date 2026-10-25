package com.rhys.chat.data.session

import com.android.common.utils.MMKVUtils

/**
 * 持久化“上一次登录并写入聊天缓存的用户 ID”。
 *
 * 仅用于判定退出/切换账号是否需要清空本地聊天数据；
 * 不在未登录时写入，不保存 token 或任何聊天内容。
 */
interface ChatOwnerStore {
    fun readOwnerId(): Long?

    fun writeOwnerId(ownerId: Long)

    fun clearOwnerId()
}

/** 生产实现：MMKV 默认实例，Key 全局唯一。 */
object MmkvChatOwnerStore : ChatOwnerStore {

    private const val KEY_OWNER_USER_ID = "chat_local_owner_user_id"

    override fun readOwnerId(): Long? =
        MMKVUtils.getLong(KEY_OWNER_USER_ID, 0L).takeIf { it > 0L }

    override fun writeOwnerId(ownerId: Long) {
        MMKVUtils.setLong(KEY_OWNER_USER_ID, ownerId)
    }

    override fun clearOwnerId() {
        MMKVUtils.removeValueForKey(KEY_OWNER_USER_ID)
    }
}
