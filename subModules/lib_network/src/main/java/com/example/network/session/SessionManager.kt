package com.example.network.session

import com.example.network.AuthException
import com.example.network.bean.AuthResponse
import com.example.network.bean.UserDto
import com.example.network.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * 全局登录态管理。
 *
 * - restore()：启动时恢复会话；有缓存先展示，再向后端校验。
 * - login/register：成功后保存 token 与用户信息并更新状态。
 * - logout()：清空本地会话；通过 generation 防止进行中的 restore 把状态写回。
 */
object SessionManager {

    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val repository = AuthRepository()
    private val authMutex = Mutex()

    /**
     * 会话代际号。logout / 新的登录流程会使旧的 restore 结果失效，
     * 避免“退出后又被异步恢复成已登录”。
     */
    private val generation = AtomicInteger(0)

    fun currentToken(): String? = repository.currentToken()

    suspend fun restore() {
        authMutex.withLock {
            val startGeneration = generation.get()
            val token = repository.currentToken()
            if (token.isNullOrBlank()) {
                if (generation.get() == startGeneration) {
                    _state.value = AuthState.LoggedOut
                }
                return
            }

            val cached = repository.cachedUser()
            if (cached != null && generation.get() == startGeneration) {
                _state.value = AuthState.LoggedIn(cached)
            }

            try {
                val user = repository.me()
                if (generation.get() != startGeneration || repository.currentToken() != token) return
                repository.saveSession(AuthResponse(token = token, user = user))
                _state.value = AuthState.LoggedIn(user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthException) {
                if (generation.get() != startGeneration) return
                if (e.isUnauthorized) {
                    repository.clearSession()
                    _state.value = AuthState.LoggedOut
                } else if (cached == null) {
                    _state.value = AuthState.LoggedOut
                }
            } catch (e: Exception) {
                if (generation.get() == startGeneration && cached == null) {
                    _state.value = AuthState.LoggedOut
                }
            }
        }
    }

    suspend fun login(username: String, password: String) {
        authMutex.withLock {
            generation.incrementAndGet()
            val response = repository.login(username, password)
            repository.saveSession(response)
            _state.value = AuthState.LoggedIn(response.user)
        }
    }

    suspend fun register(username: String, password: String, nickname: String?) {
        authMutex.withLock {
            generation.incrementAndGet()
            val response = repository.register(username, password, nickname)
            repository.saveSession(response)
            _state.value = AuthState.LoggedIn(response.user)
        }
    }

    fun logout() {
        generation.incrementAndGet()
        repository.clearSession()
        _state.value = AuthState.LoggedOut
    }

    /**
     * 资料更新成功后同步本地缓存的用户信息。
     * 不改变登录代际：这不是新的登录流程，不能使进行中的 restore 结果失效。
     *
     * 写缓存前必须确认仍处于该用户的登录态：资料请求在途期间可能已登出
     * 或切换账号，此时旧响应不能覆盖新会话。
     */
    suspend fun updateUser(user: UserDto) {
        authMutex.withLock {
            val token = repository.currentToken()
            if (token.isNullOrBlank()) {
                _state.value = AuthState.LoggedOut
                return
            }
            val current = _state.value
            if (current !is AuthState.LoggedIn || current.user.id != user.id) {
                return
            }
            repository.saveSession(AuthResponse(token = token, user = user))
            _state.value = AuthState.LoggedIn(user)
        }
    }
}
