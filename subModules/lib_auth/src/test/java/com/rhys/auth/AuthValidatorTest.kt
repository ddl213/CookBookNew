package com.rhys.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun login_accepts_valid_credentials() {
        val result = AuthValidator.validateLogin("chef", "123456")
        assertTrue(result.isValid)
        assertNull(result.errorMessage)
    }

    @Test
    fun login_trims_username_before_length_check() {
        val result = AuthValidator.validateLogin("  chef  ", "123456")
        assertTrue(result.isValid)
    }

    @Test
    fun login_rejects_short_username() {
        val result = AuthValidator.validateLogin("ab", "123456")
        assertFalse(result.isValid)
        assertEquals("用户名长度应为 3-32 个字符", result.errorMessage)
    }

    @Test
    fun login_rejects_too_long_username() {
        val result = AuthValidator.validateLogin("a".repeat(33), "123456")
        assertFalse(result.isValid)
        assertEquals("用户名长度应为 3-32 个字符", result.errorMessage)
    }

    @Test
    fun login_rejects_blank_username() {
        val result = AuthValidator.validateLogin("   ", "123456")
        assertFalse(result.isValid)
        assertEquals("请输入用户名", result.errorMessage)
    }

    @Test
    fun login_rejects_short_password() {
        val result = AuthValidator.validateLogin("chef", "12345")
        assertFalse(result.isValid)
        assertEquals("密码长度不能少于 6 位", result.errorMessage)
    }

    @Test
    fun login_rejects_empty_password() {
        val result = AuthValidator.validateLogin("chef", "")
        assertFalse(result.isValid)
        assertEquals("请输入密码", result.errorMessage)
    }

    @Test
    fun register_accepts_valid_input_with_optional_nickname() {
        val result = AuthValidator.validateRegister("chef", "", "123456", "123456")
        assertTrue(result.isValid)
    }

    @Test
    fun register_accepts_nickname() {
        val result = AuthValidator.validateRegister("chef", " 小厨 ", "123456", "123456")
        assertTrue(result.isValid)
    }

    @Test
    fun register_rejects_too_long_nickname() {
        val result = AuthValidator.validateRegister("chef", "名".repeat(33), "123456", "123456")
        assertFalse(result.isValid)
        assertEquals("昵称不能超过 32 个字符", result.errorMessage)
    }

    @Test
    fun register_rejects_mismatched_confirm_password() {
        val result = AuthValidator.validateRegister("chef", "", "123456", "123457")
        assertFalse(result.isValid)
        assertEquals("两次输入的密码不一致", result.errorMessage)
    }

    @Test
    fun register_rejects_empty_confirm_password() {
        val result = AuthValidator.validateRegister("chef", "", "123456", "")
        assertFalse(result.isValid)
        assertEquals("请再次输入密码", result.errorMessage)
    }
}
