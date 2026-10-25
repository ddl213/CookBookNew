package com.rhys.chat.data

import com.example.network.session.AuthState
import com.example.network.session.SessionManager

/**
 * 聊天缓存 owner 来源。
 *
 * 默认读取 [SessionManager.state]：只有 `LoggedIn` 才返回用户 ID；
 * `Unknown` / `LoggedOut` 返回 null，调用方不得读写本地聊天缓存。
 * 单元测试可注入固定/可变 owner 以验证账号隔离。
 */
fun interface ChatOwnerProvider {
    fun currentOwnerId(): Long?
}

/** 生产默认实现：从全局登录态读取当前用户 ID。 */
object SessionChatOwnerProvider : ChatOwnerProvider {
    override fun currentOwnerId(): Long? =
        (SessionManager.state.value as? AuthState.LoggedIn)?.user?.id
}
