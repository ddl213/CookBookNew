package com.example.network

import com.example.network.bean.ApiError
import com.example.network.bean.ApiErrorResponse
import com.google.gson.Gson
import retrofit2.HttpException
import java.io.IOException

/**
 * 将网络异常转换为可展示的 AuthException。
 *
 * 安全约束：
 * - 不打印请求体、密码或 token；
 * - 不把服务端堆栈或原始异常信息直接展示给用户。
 */
internal object AuthErrorMapper {

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
            code == "USERNAME_TAKEN" -> "用户名已被占用"
            status == 400 -> validationMessage(error)
            status == 429 -> "请求过于频繁，请稍后再试"
            !error?.message.isNullOrBlank() -> error?.message.orEmpty()
            else -> "请求失败（HTTP $status）"
        }

        return AuthException(
            code = code,
            message = message,
            httpStatus = status,
            cause = exception
        )
    }

    private fun parseError(exception: HttpException): ApiError? {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        if (body.isNullOrBlank()) return null
        return runCatching { gson.fromJson(body, ApiErrorResponse::class.java)?.error }.getOrNull()
    }

    /**
     * 400 优先使用 details 中可读的字段校验信息；
     * 没有 details 时回退到服务端 message。
     */
    private fun validationMessage(error: ApiError?): String {
        val details = error?.details.orEmpty().mapNotNull { detail ->
            val detailMessage = detail?.message?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val path = detail?.path?.takeIf { it.isNotBlank() }
            if (path.isNullOrBlank()) detailMessage else "$path：$detailMessage"
        }
        if (details.isNotEmpty()) return details.joinToString("；")
        return error?.message?.takeIf { it.isNotBlank() } ?: "提交的信息不合法"
    }
}
