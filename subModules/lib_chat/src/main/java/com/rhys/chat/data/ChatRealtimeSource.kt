package com.rhys.chat.data

import com.example.network.bean.ChatMessageDto
import com.example.network.bean.ChatNotificationDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** 实时连接状态；本期真实连接未实现，默认值为 [UNAVAILABLE]。 */
enum class ChatRealtimeState {
    UNAVAILABLE,
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

/** 预留的实时事件信封中的事件类型。 */
sealed interface ChatRealtimeEvent {
    data class MessageCreated(val message: ChatMessageDto) : ChatRealtimeEvent

    data class NotificationCreated(
        val notification: ChatNotificationDto
    ) : ChatRealtimeEvent
}

/**
 * 实时消息扩展点。
 *
 * 服务端 WebSocket 就绪前只定义接口，不建立连接、不重连、不推送。
 * 页面应把 [ChatRealtimeState.UNAVAILABLE] 展示为“实时消息暂不可用”，
 * 不能伪造已连接或已接收状态。
 */
interface ChatRealtimeSource {

    val connectionState: Flow<ChatRealtimeState>

    val events: Flow<ChatRealtimeEvent>

    suspend fun connect()

    suspend fun disconnect()
}

/** 本期默认实现：状态明确为不可用，事件流为空，连接/断开为空操作。 */
class UnavailableChatRealtimeSource : ChatRealtimeSource {

    override val connectionState: Flow<ChatRealtimeState> =
        flowOf(ChatRealtimeState.UNAVAILABLE)

    override val events: Flow<ChatRealtimeEvent> = emptyFlow()

    override suspend fun connect() = Unit

    override suspend fun disconnect() = Unit
}
