package com.example.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** 社交接口新增错误码的可展示文案。 */
class SocialErrorMapperTest {

    @Test
    fun `recipe not visible maps to recipient hint`() {
        val exception = map(403, "RECIPE_NOT_VISIBLE")
        assertEquals("RECIPE_NOT_VISIBLE", exception.code)
        assertEquals("好友无法查看该菜谱，请确认可见范围", exception.message)
    }

    @Test
    fun `share forbidden explains author only rule`() {
        val exception = map(403, "SHARE_FORBIDDEN")
        assertEquals("SHARE_FORBIDDEN", exception.code)
        assertEquals("只有菜谱作者可以分享", exception.message)
    }

    @Test
    fun `comment not found maps to deletion hint`() {
        val exception = map(404, "COMMENT_NOT_FOUND")
        assertEquals("COMMENT_NOT_FOUND", exception.code)
        assertEquals("评论不存在或已被删除", exception.message)
    }

    @Test
    fun `comment forbidden maps to permission hint`() {
        val exception = map(403, "COMMENT_FORBIDDEN")
        assertEquals("COMMENT_FORBIDDEN", exception.code)
        assertEquals("没有权限操作该评论", exception.message)
    }

    @Test
    fun `existing recipe not found mapping is unchanged`() {
        val exception = map(404, "RECIPE_NOT_FOUND")
        assertEquals("RECIPE_NOT_FOUND", exception.code)
        assertEquals("菜谱不存在或已删除", exception.message)
    }

    private fun map(status: Int, code: String): AuthException {
        val body = """{"error":{"code":"$code","message":"server detail"}}"""
            .toResponseBody("application/json".toMediaType())
        return RelationErrorMapper.map(
            HttpException(Response.error<Unit>(status, body)),
            unauthorizedMessage = "登录已失效，请重新登录"
        )
    }
}
