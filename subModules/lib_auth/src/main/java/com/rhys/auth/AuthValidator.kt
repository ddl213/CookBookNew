package com.rhys.auth

/**
 * 表单校验结果。
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
) {
    companion object {
        val valid: ValidationResult = ValidationResult(true)

        fun invalid(message: String): ValidationResult = ValidationResult(false, message)
    }
}

/**
 * 登录 / 注册表单的纯 Kotlin 校验逻辑，便于 JVM 单元测试。
 * 用户名、昵称按 trim 后的值校验；密码保持原值（后端不 trim 密码）。
 */
object AuthValidator {

    const val USERNAME_MIN_LENGTH = 3
    const val USERNAME_MAX_LENGTH = 32
    const val PASSWORD_MIN_LENGTH = 6
    const val NICKNAME_MAX_LENGTH = 32

    fun validateLogin(username: String, password: String): ValidationResult {
        validateUsername(username)?.let { return ValidationResult.invalid(it) }
        validatePassword(password)?.let { return ValidationResult.invalid(it) }
        return ValidationResult.valid
    }

    fun validateRegister(
        username: String,
        nickname: String,
        password: String,
        confirmPassword: String
    ): ValidationResult {
        validateUsername(username)?.let { return ValidationResult.invalid(it) }
        validateNickname(nickname)?.let { return ValidationResult.invalid(it) }
        validatePassword(password)?.let { return ValidationResult.invalid(it) }
        if (confirmPassword.isEmpty()) {
            return ValidationResult.invalid("请再次输入密码")
        }
        if (password != confirmPassword) {
            return ValidationResult.invalid("两次输入的密码不一致")
        }
        return ValidationResult.valid
    }

    private fun validateUsername(username: String): String? {
        val value = username.trim()
        if (value.isEmpty()) return "请输入用户名"
        if (value.length !in USERNAME_MIN_LENGTH..USERNAME_MAX_LENGTH) {
            return "用户名长度应为 $USERNAME_MIN_LENGTH-$USERNAME_MAX_LENGTH 个字符"
        }
        return null
    }

    private fun validatePassword(password: String): String? {
        if (password.isEmpty()) return "请输入密码"
        if (password.length < PASSWORD_MIN_LENGTH) {
            return "密码长度不能少于 $PASSWORD_MIN_LENGTH 位"
        }
        return null
    }

    private fun validateNickname(nickname: String): String? {
        if (nickname.trim().length > NICKNAME_MAX_LENGTH) {
            return "昵称不能超过 $NICKNAME_MAX_LENGTH 个字符"
        }
        return null
    }
}
