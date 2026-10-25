package com.rhys.chat.data

import com.android.common.utils.LogUtils
import com.rhys.chat.BuildConfig
import java.util.Locale

/** 数据源类型。 */
enum class ChatDataSourceKind {
    MOCK,
    REMOTE
}

/**
 * 配置解析结果。
 *
 * [fallback] 为 true 表示输入值不是合法数据源名称，已回退 [ChatDataSourceKind.REMOTE]。
 */
data class ChatDataSourceResolution(
    val kind: ChatDataSourceKind,
    val rawValue: String?,
    val fallback: Boolean
)

/**
 * 聊天数据源配置。
 *
 * 值来自 `BuildConfig.CHAT_DATA_SOURCE`，由 Gradle 属性 `chatDataSource` 在构建期写入：
 * debug 默认 mock、release 默认 remote，可显式覆盖。非法值回退 remote 并记录警告。
 * 服务地址不在本类维护，统一复用 `NetworkConfig.baseUrl` / Gradle `apiBaseUrl`。
 */
object ChatDataSourceConfig {

    const val SOURCE_MOCK = "mock"
    const val SOURCE_REMOTE = "remote"
    private const val TAG = "ChatDataSourceConfig"

    fun resolve(raw: String?): ChatDataSourceResolution {
        val normalized = raw?.trim()?.lowercase(Locale.ROOT)
        return when (normalized) {
            SOURCE_MOCK -> ChatDataSourceResolution(
                kind = ChatDataSourceKind.MOCK,
                rawValue = raw,
                fallback = false
            )
            SOURCE_REMOTE -> ChatDataSourceResolution(
                kind = ChatDataSourceKind.REMOTE,
                rawValue = raw,
                fallback = false
            )
            else -> ChatDataSourceResolution(
                kind = ChatDataSourceKind.REMOTE,
                rawValue = raw,
                fallback = true
            )
        }
    }

    /**
     * 按配置创建数据源。
     *
     * 工厂参数仅用于测试替换，正式调用使用默认实现。
     */
    fun create(
        raw: String? = BuildConfig.CHAT_DATA_SOURCE,
        onWarning: (String) -> Unit = { message -> LogUtils.w(message, TAG) },
        mockFactory: () -> ChatDataSource = {
            // Mock 的“自己”必须是当前登录用户；未登录时回退演示 ID。
            val ownerId = SessionChatOwnerProvider.currentOwnerId()
                ?: MockChatDataSource.DEFAULT_LOCAL_USER_ID
            MockChatDataSource(localUserId = ownerId)
        },
        remoteFactory: () -> ChatDataSource = { RemoteChatDataSource() }
    ): ChatDataSource {
        val resolution = resolve(raw)
        if (resolution.fallback) {
            onWarning("未知的 chatDataSource='$raw'，已回退 remote")
        }
        return when (resolution.kind) {
            ChatDataSourceKind.MOCK -> mockFactory()
            ChatDataSourceKind.REMOTE -> remoteFactory()
        }
    }
}
