package com.example.network

import com.example.network.bean.ApiErrorResponse
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

/**
 * 聊天与通知接口的错误映射。
 *
 * 复用项目统一的可展示异常 [AuthException]（同时承载 code 与 httpStatus），
 * 将后端英文错误码转换为中文文案。不记录 token、消息正文或会话内容。
 */
object ChatErrorMapper {

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
                message = "聊天请求失败，请稍后重试",
                cause = throwable
            )
        }
    }

    private fun mapHttp(exception: HttpException, unauthorizedMessage: String): AuthException {
        val status = exception.code()
        val error = parseError(exception)
        val code = error?.code ?: "HTTP_$status"

        val message = when {
            status == 401 -> unauthorizedMessage
            code == "NOT_FRIENDS" -> "你们还不是好友，无法发送消息"
            code == "CHAT_FORBIDDEN" -> "没有权限访问该会话"
            code == "CHAT_NOT_FOUND" -> "会话不存在或已被删除"
            code == "VALIDATION_ERROR" -> "消息内容不合法，请检查后重试"
            code == "FORBIDDEN" -> "没有权限执行该操作"
            status == 400 -> "消息内容不合法，请检查后重试"
            status == 403 -> "没有权限执行该操作"
            status == 404 -> "会话或消息不存在"
            status == 409 -> "消息已提交，请勿重复发送"
            status == 422 -> "消息内容不合法，请检查后重试"
            status == 429 -> "请求过于频繁，请稍后再试"
            status >= 500 -> "服务器暂时不可用，请稍后重试"
            else -> "聊天请求失败（HTTP $status）"
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
