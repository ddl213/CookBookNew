package com.example.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** 聊天与预订通知接口错误码的可展示文案。 */
class ChatErrorMapperTest {

    @Test
    fun `401 uses caller unauthorized message`() {
        val exception = map(401, "UNAUTHORIZED")
        assertEquals(401, exception.httpStatus)
        assertTrue(exception.isUnauthorized)
        assertEquals("登录已失效，请重新登录", exception.message)
    }

    @Test
    fun `not friends maps to friendship hint`() {
        val exception = map(403, "NOT_FRIENDS")
        assertEquals("NOT_FRIENDS", exception.code)
        assertEquals("你们还不是好友，无法发送消息", exception.message)
    }

    @Test
    fun `chat forbidden maps to permission hint`() {
        val exception = map(403, "CHAT_FORBIDDEN")
        assertEquals("CHAT_FORBIDDEN", exception.code)
        assertEquals("没有权限访问该会话", exception.message)
    }

    @Test
    fun `chat not found maps to deletion hint`() {
        val exception = map(404, "CHAT_NOT_FOUND")
        assertEquals("CHAT_NOT_FOUND", exception.code)
        assertEquals("会话不存在或已被删除", exception.message)
    }

    @Test
    fun `400 validation error maps to content hint`() {
        val exception = map(400, "VALIDATION_ERROR")
        assertEquals("VALIDATION_ERROR", exception.code)
        assertEquals("消息内容不合法，请检查后重试", exception.message)
    }

    @Test
    fun `422 validation error maps to content hint`() {
        val exception = map(422, "VALIDATION_ERROR")
        assertEquals(422, exception.httpStatus)
        assertEquals("消息内容不合法，请检查后重试", exception.message)
    }

    @Test
    fun `429 maps to rate limit hint`() {
        val exception = map(429, "RATE_LIMITED")
        assertEquals("请求过于频繁，请稍后再试", exception.message)
    }

    @Test
    fun `io exception maps to network hint`() {
        val exception = ChatErrorMapper.map(
            IOException("connection refused"),
            unauthorizedMessage = "登录已失效，请重新登录"
        )
        assertEquals("NETWORK_ERROR", exception.code)
        assertEquals("无法连接服务器，请确认网络和后端服务", exception.message)
    }

    @Test
    fun `500 maps to server unavailable hint`() {
        val exception = map(500, "INTERNAL_ERROR")
        assertEquals("服务器暂时不可用，请稍后重试", exception.message)
    }

    private fun map(status: Int, code: String): AuthException {
        val body = """{"error":{"code":"$code","message":"server detail"}}"""
            .toResponseBody("application/json".toMediaType())
        return ChatErrorMapper.map(
            HttpException(Response.error<Unit>(status, body)),
            unauthorizedMessage = "登录已失效，请重新登录"
        )
    }
}
