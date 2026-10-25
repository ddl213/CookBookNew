package com.example.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** 订单接口契约 §2.3 错误码与 HTTP 回退文案。 */
class OrderErrorMapperTest {

    private val unauthorized = "登录已失效，请重新登录"

    @Test
    fun `401 uses caller unauthorized message and keeps status`() {
        val exception = map(401, "UNAUTHORIZED")
        assertEquals("UNAUTHORIZED", exception.code)
        assertEquals(401, exception.httpStatus)
        assertTrue(exception.isUnauthorized)
        assertEquals(unauthorized, exception.message)
    }

    @Test
    fun `validation error maps to order validation hint`() {
        assertCodeAndMessage(400, "VALIDATION_ERROR", "VALIDATION_ERROR", "订单信息不合法，请检查后重试")
    }

    @Test
    fun `mixed sellers maps to single friend hint`() {
        assertCodeAndMessage(400, "MIXED_SELLERS", "MIXED_SELLERS", "一单只能预订同一位好友的菜品")
    }

    @Test
    fun `own recipe maps to self reservation hint`() {
        assertCodeAndMessage(400, "CANNOT_ORDER_OWN_RECIPE", "CANNOT_ORDER_OWN_RECIPE", "不能预订自己发布的菜谱")
    }

    @Test
    fun `not friends maps to friendship hint`() {
        assertCodeAndMessage(403, "NOT_FRIENDS", "NOT_FRIENDS", "你们还不是好友，无法预订")
    }

    @Test
    fun `forbidden action maps to permission hint`() {
        assertCodeAndMessage(403, "FORBIDDEN_ORDER_ACTION", "FORBIDDEN_ORDER_ACTION", "没有权限执行该订单操作")
    }

    @Test
    fun `recipe not found maps to unavailable recipe hint`() {
        assertCodeAndMessage(404, "RECIPE_NOT_FOUND", "RECIPE_NOT_FOUND", "菜谱不存在或当前不可预订")
    }

    @Test
    fun `order not found maps to access hint`() {
        assertCodeAndMessage(404, "ORDER_NOT_FOUND", "ORDER_NOT_FOUND", "订单不存在或你没有访问权限")
    }

    @Test
    fun `order item not found maps to item hint`() {
        assertCodeAndMessage(404, "ORDER_ITEM_NOT_FOUND", "ORDER_ITEM_NOT_FOUND", "订单明细不存在或不属于该订单")
    }

    @Test
    fun `idempotency key reused maps to pending hint`() {
        assertCodeAndMessage(
            409,
            "IDEMPOTENCY_KEY_REUSED",
            "IDEMPOTENCY_KEY_REUSED",
            "该预订请求已提交过且内容不同，请先处理待确认订单"
        )
    }

    @Test
    fun `version conflict maps to refresh hint`() {
        assertCodeAndMessage(409, "ORDER_VERSION_CONFLICT", "ORDER_VERSION_CONFLICT", "订单状态已更新，请刷新后重试")
    }

    @Test
    fun `invalid item status maps to status hint`() {
        assertCodeAndMessage(409, "INVALID_ORDER_ITEM_STATUS", "INVALID_ORDER_ITEM_STATUS", "当前菜品状态不允许该操作")
    }

    @Test
    fun `not cancellable maps to all pending hint`() {
        assertCodeAndMessage(409, "ORDER_NOT_CANCELLABLE", "ORDER_NOT_CANCELLABLE", "仅全部待接受的订单可以取消")
    }

    @Test
    fun `unknown business code falls back to http status hint`() {
        val exception = map(400, "SOMETHING_NEW")
        assertEquals("SOMETHING_NEW", exception.code)
        assertEquals(400, exception.httpStatus)
        assertEquals("订单请求参数不合法，请检查后重试", exception.message)
    }

    @Test
    fun `http 418 falls back to generic message`() {
        val exception = map(418, "TEAPOT")
        assertEquals(418, exception.httpStatus)
        assertEquals("订单请求失败（HTTP 418）", exception.message)
    }

    @Test
    fun `429 maps to rate limit hint`() {
        assertEquals("请求过于频繁，请稍后再试", map(429, "RATE_LIMITED").message)
    }

    @Test
    fun `500 maps to server unavailable hint`() {
        val exception = map(500, "INTERNAL_ERROR")
        assertEquals(500, exception.httpStatus)
        assertEquals("服务器暂时不可用，请稍后重试", exception.message)
    }

    @Test
    fun `io exception maps to network error without status`() {
        val exception = OrderErrorMapper.map(IOException("connection refused"), unauthorized)
        assertEquals("NETWORK_ERROR", exception.code)
        assertEquals("无法连接服务器，请确认网络和后端服务", exception.message)
        assertNull(exception.httpStatus)
    }

    @Test
    fun `other throwable maps to unknown error`() {
        val exception = OrderErrorMapper.map(IllegalStateException("boom"), unauthorized)
        assertEquals("UNKNOWN_ERROR", exception.code)
        assertEquals("订单请求失败，请稍后重试", exception.message)
    }

    @Test
    fun `existing auth exception is returned unchanged`() {
        val existing = AuthException(code = "ORDER_NOT_FOUND", message = "已映射", httpStatus = 404)
        assertTrue(OrderErrorMapper.map(existing, unauthorized) === existing)
    }

    private fun assertCodeAndMessage(status: Int, code: String, expectedCode: String, expectedMessage: String) {
        val exception = map(status, code)
        assertEquals(expectedCode, exception.code)
        assertEquals(status, exception.httpStatus)
        assertEquals(expectedMessage, exception.message)
    }

    private fun map(status: Int, code: String): AuthException {
        val body = """{"error":{"code":"$code","message":"server detail"}}"""
            .toResponseBody("application/json".toMediaType())
        return OrderErrorMapper.map(
            HttpException(Response.error<Unit>(status, body)),
            unauthorizedMessage = unauthorized
        )
    }
}
