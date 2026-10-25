package com.rhys.chat.data.session

import android.content.Context
import com.android.common.utils.LogUtils
import com.example.network.session.AuthState
import com.example.network.session.SessionManager
import com.rhys.chat.data.local.ChatDatabase
import com.rhys.chat.data.local.ChatDatabaseProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** 登录态变化时对本地聊天缓存采取的动作。 */
enum class ChatSessionAction {
    /** 不清理也不记录（Unknown、LoggedOut 但 token 仍在等不明确场景）。 */
    NONE,

    /** 仅记录当前登录用户为缓存 owner。 */
    RECORD_OWNER,

    /** 清空所有旧聊天缓存并记录新的登录用户。 */
    CLEAR_AND_RECORD_OWNER,

    /** 明确退出登录：清空所有聊天缓存并删除 owner 记录。 */
    CLEAR_AND_FORGET_OWNER
}

/** 纯判定逻辑，便于独立验证。 */
object ChatSessionRules {

    fun decide(
        previousOwnerId: Long?,
        state: AuthState,
        hasToken: Boolean
    ): ChatSessionAction = when (state) {
        is AuthState.LoggedIn -> {
            if (previousOwnerId != null && previousOwnerId != state.user.id) {
                ChatSessionAction.CLEAR_AND_RECORD_OWNER
            } else {
                ChatSessionAction.RECORD_OWNER
            }
        }
        is AuthState.LoggedOut -> {
            // 只有“明确退出”（token 已清空）才清理；Unknown 或 token 仍在时不动作。
            if (!hasToken) {
                ChatSessionAction.CLEAR_AND_FORGET_OWNER
            } else {
                ChatSessionAction.NONE
            }
        }
        is AuthState.Unknown -> ChatSessionAction.NONE
    }
}

/**
 * 应用级登录态守卫：按登录用户隔离聊天缓存。
 *
 * - `LoggedIn`：记录 owner ID 到 MMKV；账号与上次不同时先清空全部聊天表。
 * - `LoggedOut` 且 token 为空（明确退出）：清空全部聊天表并删除 owner 记录。
 * - `LoggedOut` 但 token 仍在、以及 `Unknown`：不清理。
 *
 * 数据库清理异常被捕获，不得导致应用崩溃；不记录聊天内容或 token。
 */
class ChatSessionGuard(
    private val scope: CoroutineScope,
    private val databaseProvider: () -> ChatDatabase = { ChatDatabaseProvider.get() },
    private val ownerStore: ChatOwnerStore = MmkvChatOwnerStore,
    private val states: Flow<AuthState> = SessionManager.state,
    private val currentToken: () -> String? = { SessionManager.currentToken() },
    private val onError: (Throwable) -> Unit = { error ->
        LogUtils.w("聊天缓存清理失败：${error.javaClass.simpleName}", TAG)
    }
) {

    // 读取失败不得阻断应用启动；视为“无历史 owner”，后续 LoggedIn 会重新记录。
    @Volatile
    private var previousOwnerId: Long? = try {
        ownerStore.readOwnerId()
    } catch (_: Exception) {
        null
    }

    @Volatile
    private var job: Job? = null

    /** 开始收集登录态；重复调用返回同一 Job。 */
    fun start(): Job {
        job?.let { return it }
        return synchronized(this) {
            job ?: scope.launch {
                try {
                    states.collect { state -> handleSafely(state) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onError(e)
                }
            }.also { job = it }
        }
    }

    /** 处理单个登录态；供单元测试直接驱动。 */
    internal suspend fun handle(state: AuthState) {
        val action = ChatSessionRules.decide(
            previousOwnerId = previousOwnerId,
            state = state,
            hasToken = !currentToken().isNullOrBlank()
        )
        when (action) {
            ChatSessionAction.NONE -> Unit
            ChatSessionAction.RECORD_OWNER -> {
                val ownerId = (state as? AuthState.LoggedIn)?.user?.id ?: return
                ownerStore.writeOwnerId(ownerId)
                previousOwnerId = ownerId
            }
            ChatSessionAction.CLEAR_AND_RECORD_OWNER -> {
                val ownerId = (state as? AuthState.LoggedIn)?.user?.id ?: return
                databaseProvider().chatDao().clearAll()
                ownerStore.writeOwnerId(ownerId)
                previousOwnerId = ownerId
            }
            ChatSessionAction.CLEAR_AND_FORGET_OWNER -> {
                databaseProvider().chatDao().clearAll()
                ownerStore.clearOwnerId()
                previousOwnerId = null
            }
        }
    }

    private suspend fun handleSafely(state: AuthState) {
        try {
            handle(state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e)
        }
    }

    companion object {
        private const val TAG = "ChatSessionGuard"

        @Volatile
        private var appInstance: ChatSessionGuard? = null

        /**
         * 应用启动接线：在 MMKV 与 [com.android.common.helper.AppHelper] 初始化后调用。
         * 幂等，重复调用返回同一实例。
         */
        fun start(context: Context): ChatSessionGuard {
            appInstance?.let { return it }
            return synchronized(this) {
                appInstance ?: ChatSessionGuard(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    databaseProvider = {
                        ChatDatabaseProvider.get(context.applicationContext)
                    }
                ).also {
                    it.start()
                    appInstance = it
                }
            }
        }
    }
}
