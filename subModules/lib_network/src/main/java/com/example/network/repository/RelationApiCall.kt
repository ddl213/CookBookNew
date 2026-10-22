package com.example.network.repository

import com.example.network.RelationErrorMapper
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.Response

/** 用户/好友 Repository 的统一异常转换入口，取消异常保持原样抛出。 */
internal suspend fun <T> callRelationApi(
    unauthorizedMessage: String = "登录已失效，请重新登录",
    block: suspend () -> T
): T {
    try {
        return block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw RelationErrorMapper.map(e, unauthorizedMessage)
    }
}

/**
 * 校验 Retrofit 的 Response 是否为 2xx。
 *
 * 使用 Response<T> 的接口（如 204 删除）在 4xx 时不会自动抛 HttpException，
 * 必须显式校验，否则失败会被误当成成功。错误仍由 RelationErrorMapper 转换为
 * 可展示的 AuthException，保留 401/403/404 等映射。
 */
internal fun <T> Response<T>.requireSuccessful(
    unauthorizedMessage: String = "登录已失效，请重新登录"
): Response<T> {
    if (!isSuccessful) {
        throw RelationErrorMapper.map(HttpException(this), unauthorizedMessage)
    }
    return this
}
