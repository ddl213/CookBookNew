package com.example.network

import com.example.network.bean.ApiErrorResponse
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

/**
 * 订单接口错误映射。
 *
 * 复用统一的可展示异常 [AuthException]（同时承载 code 与 httpStatus），
 * 将契约 §2.3 的错误码转换为中文文案。不记录 token、订单备注或请求体。
 */
object OrderErrorMapper {

    private val gson = Gson()

    fun map(throwable: Throwable, unauthorizedMessage: String): AuthException {
        return when (throwable) {
            is AuthException -> throwable
            is HttpException -> mapHttp(throwable, unauthorizedMessage)
            is IOException -> AuthException(
                code = "NETWORK_ERROR",
                message = "无法连接服务器，请确认网络和后端服务",
                cause = throwable
            )
            else -> AuthException(
                code = "UNKNOWN_ERROR",
                message = "订单请求失败，请稍后重试",
                cause = throwable
            )
        }
    }

    private fun mapHttp(exception: HttpException, unauthorizedMessage: String): AuthException {
        val status = exception.code()
        val error = parseError(exception)
        val code = error?.code ?: "HTTP_$status"

        val message = when {
            status == 401 || code == "UNAUTHORIZED" -> unauthorizedMessage
            code == "VALIDATION_ERROR" -> "订单信息不合法，请检查后重试"
            code == "MIXED_SELLERS" -> "一单只能预订同一位好友的菜品"
            code == "CANNOT_ORDER_OWN_RECIPE" -> "不能预订自己发布的菜谱"
            code == "NOT_FRIENDS" -> "你们还不是好友，无法预订"
            code == "FORBIDDEN_ORDER_ACTION" -> "没有权限执行该订单操作"
            code == "RECIPE_NOT_FOUND" -> "菜谱不存在或当前不可预订"
            code == "ORDER_NOT_FOUND" -> "订单不存在或你没有访问权限"
            code == "ORDER_ITEM_NOT_FOUND" -> "订单明细不存在或不属于该订单"
            code == "IDEMPOTENCY_KEY_REUSED" -> "该预订请求已提交过且内容不同，请先处理待确认订单"
            code == "ORDER_VERSION_CONFLICT" -> "订单状态已更新，请刷新后重试"
            code == "INVALID_ORDER_ITEM_STATUS" -> "当前菜品状态不允许该操作"
            code == "ORDER_NOT_CANCELLABLE" -> "仅全部待接受的订单可以取消"
            status == 400 -> "订单请求参数不合法，请检查后重试"
            status == 403 -> "没有权限执行该订单操作"
            status == 404 -> "订单或菜谱不存在"
            status == 409 -> "订单状态已变化，请刷新后重试"
            status == 422 -> "订单信息不合法，请检查后重试"
            status == 429 -> "请求过于频繁，请稍后再试"
            status >= 500 -> "服务器暂时不可用，请稍后重试"
            else -> "订单请求失败（HTTP $status）"
        }

        return AuthException(
            code = code,
            message = message,
            httpStatus = status,
            cause = exception
        )
    }

    private fun parseError(exception: HttpException) = runCatching {
        val body = exception.response()?.errorBody()?.string()
        if (body.isNullOrBlank()) {
            null
        } else {
            gson.fromJson(body, ApiErrorResponse::class.java)?.error
        }
    }.getOrNull()
}
