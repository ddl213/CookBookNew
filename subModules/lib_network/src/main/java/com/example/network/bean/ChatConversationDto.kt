package com.example.network.bean

/**
 * 聊天会话列表条目，对应 GET chat/conversations 的 items。
 *
 * peer 为会话另一方（好友）的公开资料；字段可空以容忍后端缺省，
 * 页面应提供安全的占位展示。updatedAt 为 ISO-8601 字符串。
 */
data class ChatConversationDto(
    val id: Long = 0L,
    val peer: UserDto? = null,
    val lastMessage: ChatMessageDto? = null,
    val updatedAt: String? = null
)
