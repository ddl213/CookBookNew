package com.example.network.api

import com.example.network.bean.ChatConversationDto
import com.example.network.bean.ChatMessageDto
import com.example.network.bean.CursorPage
import com.example.network.bean.OpenChatConversationBody
import com.example.network.bean.SendChatMessageBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 好友一对一文字聊天接口。
 *
 * baseUrl 已包含 /api/v1/，接口使用相对路径；全部需要登录。
 * 好友关系与会话参与方由后端校验，客户端不得以隐藏入口代替权限判定。
 */
interface ChatApi {

    /** 会话列表，按 updatedAt 倒序；cursor 为空表示第一页。 */
    @GET("chat/conversations")
    suspend fun listConversations(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String?
    ): CursorPage<ChatConversationDto>

    /** 打开或创建与好友的会话；已存在时返回现有会话。 */
    @POST("chat/conversations")
    suspend fun openConversation(
        @Body body: OpenChatConversationBody
    ): ChatConversationDto

    /** 历史消息，按 createdAt 升序返回；before 指向更早一页。 */
    @GET("chat/conversations/{conversationId}/messages")
    suspend fun listMessages(
        @Path("conversationId") conversationId: Long,
        @Query("limit") limit: Int,
        @Query("before") before: String?
    ): CursorPage<ChatMessageDto>

    /**
     * 发送文字消息。服务端按 clientMessageId 幂等：
     * 重复提交返回同一条消息，不新增记录。
     */
    @POST("chat/conversations/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: Long,
        @Body body: SendChatMessageBody
    ): ChatMessageDto
}
