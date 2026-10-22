package com.example.network.session

import com.android.common.utils.MMKVUtils
import com.example.network.bean.UserDto
import com.google.gson.Gson

/**
 * 登录信息本地存储（MMKV）。
 *
 * 仅保存 token 与用户公开资料 JSON；本阶段不做额外加密。
 * 读取时对空值和损坏 JSON 做容错，统一视为未登录。
 */
object SessionStore {

    private const val KEY_TOKEN = "auth_token"
    private const val KEY_USER = "auth_user"

    private val gson = Gson()

    fun token(): String? {
        return MMKVUtils.getString(KEY_TOKEN)?.takeIf { it.isNotBlank() }
    }

    fun user(): UserDto? {
        val json = MMKVUtils.getString(KEY_USER)
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, UserDto::class.java) }.getOrNull()
    }

    fun save(token: String, user: UserDto) {
        MMKVUtils.setString(KEY_TOKEN, token)
        MMKVUtils.setString(KEY_USER, gson.toJson(user))
    }

    fun clear() {
        MMKVUtils.removeValueForKey(KEY_TOKEN)
        MMKVUtils.removeValueForKey(KEY_USER)
    }
}
