package com.example.network.session

import com.example.network.bean.UserDto

/**
 * 登录态。
 * Unknown 表示启动恢复尚未完成，UI 应显示过渡状态而不是擅自当作已登录。
 */
sealed interface AuthState {

    object Unknown : AuthState

    object LoggedOut : AuthState

    data class LoggedIn(val user: UserDto) : AuthState
}
