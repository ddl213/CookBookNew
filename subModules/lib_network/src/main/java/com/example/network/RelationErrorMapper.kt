package com.example.network

import com.example.network.bean.ApiErrorResponse
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

/**
 * 用户/好友接口的错误映射。
 *
 * 复用项目统一的可展示异常 [AuthException]（它同时承载 code 与 httpStatus），
 * 将后端英文错误码转换为中文文案，不把服务端内部细节直接展示给用户。
 */
internal object RelationErrorMapper {

    private val gson = Gson()

    fun map(throwable: Throwable, unauthorizedMessage: String): AuthException {
        return when (throwable) {
            is AuthException -> throwable
            is HttpException -> mapHttp(throwable, unauthorizedMessage)
            is IOException -> AuthException(
                code = "NETWORK_ERROR",
                message = "无法连接服务器，请确认后端已启动",
                cause = throwable
            )
            else -> AuthException(
                code = "UNKNOWN_ERROR",
                message = "网络请求失败，请稍后重试",
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
            code == "CANNOT_FRIEND_SELF" -> "不能添加自己为好友"
            code == "CANNOT_REMOVE_SELF" -> "不能解除自己的好友关系"
            code == "ALREADY_FRIENDS" -> "你们已经是好友了"
            code == "FRIEND_REQUEST_EXISTS" -> "已发出好友申请，请等待对方处理"
            code == "REQUEST_NOT_PENDING" -> "该申请已被处理，请刷新列表"
            code == "FRIEND_REQUEST_NOT_FOUND" -> "好友申请不存在或已被删除"
            code == "FRIEND_NOT_FOUND" -> "对方不在你的好友列表中"
            code == "USER_NOT_FOUND" -> "用户不存在或已删除"
            code == "NOT_FRIENDS" -> "你们还不是好友，无法查看该内容"
            code == "FORBIDDEN" -> "没有权限执行该操作"
            status == 400 -> "请求内容不合法，请检查后重试"
            status == 403 -> "没有权限执行该操作"
            status == 404 -> "请求的内容不存在"
            status == 409 -> "当前状态下无法完成操作"
            status == 429 -> "请求过于频繁，请稍后再试"
            status >= 500 -> "服务器暂时不可用，请稍后重试"
            else -> "请求失败（HTTP $status）"
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
