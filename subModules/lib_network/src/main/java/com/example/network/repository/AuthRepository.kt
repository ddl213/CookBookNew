package com.example.network.repository

import com.example.network.ApiClient
import com.example.network.AuthErrorMapper
import com.example.network.bean.AuthResponse
import com.example.network.bean.LoginRequest
import com.example.network.bean.RegisterRequest
import com.example.network.bean.UserDto
import com.example.network.session.SessionStore
import kotlinx.coroutines.CancellationException

/**
 * 认证数据访问层：封装接口调用与本地会话保存，不包含任何 UI 逻辑。
 */
class AuthRepository {

    suspend fun login(username: String, password: String): AuthResponse {
        return call(unauthorizedMessage = "用户名或密码错误") {
            ApiClient.authApi.login(LoginRequest(username = username, password = password))
        }
    }

    suspend fun register(username: String, password: String, nickname: String?): AuthResponse {
        return call(unauthorizedMessage = "注册失败，请稍后重试") {
            ApiClient.authApi.register(
                RegisterRequest(
                    username = username,
                    password = password,
                    nickname = nickname?.takeIf { it.isNotBlank() }
                )
            )
        }
    }

    suspend fun me(): UserDto {
        return call(unauthorizedMessage = "登录已失效，请重新登录") {
            ApiClient.authApi.me()
        }
    }

    fun saveSession(response: AuthResponse) {
        SessionStore.save(response.token, response.user)
    }

    fun clearSession() {
        SessionStore.clear()
    }

    fun cachedUser(): UserDto? = SessionStore.user()

    fun currentToken(): String? = SessionStore.token()

    private suspend fun <T> call(
        unauthorizedMessage: String,
        block: suspend () -> T
    ): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AuthErrorMapper.map(e, unauthorizedMessage)
        }
    }
}
