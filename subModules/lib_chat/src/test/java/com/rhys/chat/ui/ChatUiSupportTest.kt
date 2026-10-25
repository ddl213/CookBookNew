package com.rhys.chat.ui

import com.rhys.chat.data.crypto.ChatCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** 聊天列表/通知页展示规则：时间分支、兜底、首字与头像地址安全处理。 */
class ChatUiSupportTest {

    private val zone = TimeZone.getTimeZone("Asia/Shanghai")
    private val locale = Locale.US
    private val yesterday = "昨天"

    @Test
    fun `today uses hour and minute`() {
        val now = millis(2026, 10, 25, 15, 30)
        val today = millis(2026, 10, 25, 9, 5)

        assertEquals("09:05", ChatUiSupport.formatListTime(today, yesterday, now, zone, locale))
    }

    @Test
    fun `yesterday uses yesterday label across day boundary`() {
        val now = millis(2026, 10, 25, 15, 30)
        val lastNight = millis(2026, 10, 24, 23, 59)

        assertEquals(yesterday, ChatUiSupport.formatListTime(lastNight, yesterday, now, zone, locale))
    }

    @Test
    fun `yesterday label works across year boundary`() {
        val now = millis(2026, 1, 1, 0, 30)
        val lastYear = millis(2025, 12, 31, 23, 0)

        assertEquals(yesterday, ChatUiSupport.formatListTime(lastYear, yesterday, now, zone, locale))
    }

    @Test
    fun `earlier dates use full date`() {
        val now = millis(2026, 10, 25, 15, 30)
        val earlier = millis(2026, 10, 1, 8, 0)
        val lastYear = millis(2025, 12, 31, 10, 0)

        assertEquals("2026-10-01", ChatUiSupport.formatListTime(earlier, yesterday, now, zone, locale))
        assertEquals("2025-12-31", ChatUiSupport.formatListTime(lastYear, yesterday, now, zone, locale))
    }

    @Test
    fun `invalid time returns null`() {
        val now = millis(2026, 10, 25, 15, 30)

        assertNull(ChatUiSupport.formatListTime(0L, yesterday, now, zone, locale))
        assertNull(ChatUiSupport.formatListTime(-1L, yesterday, now, zone, locale))
    }

    @Test
    fun `nickname falls back for blank and unavailable text`() {
        assertEquals("好友", ChatUiSupport.displayNickname(null, "好友"))
        assertEquals("好友", ChatUiSupport.displayNickname("   ", "好友"))
        assertEquals("好友", ChatUiSupport.displayNickname(ChatCipher.UNAVAILABLE_TEXT, "好友"))
        assertEquals("林小厨", ChatUiSupport.displayNickname(" 林小厨 ", "好友"))
    }

    @Test
    fun `preview falls back only for blank and keeps unavailable placeholder`() {
        assertEquals("暂无消息", ChatUiSupport.displayPreview(null, "暂无消息"))
        assertEquals("暂无消息", ChatUiSupport.displayPreview("   ", "暂无消息"))
        assertEquals("你好", ChatUiSupport.displayPreview(" 你好 ", "暂无消息"))
        assertEquals(
            ChatCipher.UNAVAILABLE_TEXT,
            ChatUiSupport.displayPreview(ChatCipher.UNAVAILABLE_TEXT, "暂无消息")
        )
    }

    @Test
    fun `notification text uses safe fallbacks`() {
        assertEquals(
            "预订通知",
            ChatUiSupport.displayNotificationTitle(ChatCipher.UNAVAILABLE_TEXT, "预订通知")
        )
        assertEquals("预订通知", ChatUiSupport.displayNotificationTitle("  ", "预订通知"))
        assertEquals("新的预订请求", ChatUiSupport.displayNotificationTitle("新的预订请求", "预订通知"))
        assertEquals("好友预订了你的菜谱", ChatUiSupport.displayNotificationBody(" 好友预订了你的菜谱 "))
        assertEquals("", ChatUiSupport.displayNotificationBody(null))
    }

    @Test
    fun `avatar url rejects blank and unavailable values`() {
        assertNull(ChatUiSupport.avatarUrlOrNull(null))
        assertNull(ChatUiSupport.avatarUrlOrNull("   "))
        assertNull(ChatUiSupport.avatarUrlOrNull(ChatCipher.UNAVAILABLE_TEXT))
        assertEquals("/avatar/2.png", ChatUiSupport.avatarUrlOrNull(" /avatar/2.png "))
    }

    @Test
    fun `unavailable text detection ignores surrounding whitespace`() {
        assertEquals(true, ChatUiSupport.isUnavailable(" ${ChatCipher.UNAVAILABLE_TEXT} "))
        assertEquals(false, ChatUiSupport.isUnavailable("林小厨"))
        assertEquals(false, ChatUiSupport.isUnavailable(null))
    }

    @Test
    fun `first character keeps surrogate pair intact`() {
        val emoji = "😀"
        val text = "${emoji}小厨"

        val first = ChatUiSupport.firstCharacter(text)

        assertEquals(emoji, first)
        assertEquals(2, first.length)
        assertEquals("林", ChatUiSupport.firstCharacter("林小厨"))
        assertEquals("", ChatUiSupport.firstCharacter(""))
    }

    @Test
    fun `avatar initial uses safe nickname and keeps emoji`() {
        assertEquals("好", ChatUiSupport.avatarInitial(null, "好友"))
        assertEquals("好", ChatUiSupport.avatarInitial(ChatCipher.UNAVAILABLE_TEXT, "好友"))
        assertEquals("林", ChatUiSupport.avatarInitial(" 林小厨 ", "好友"))
        assertEquals("😀", ChatUiSupport.avatarInitial("😀小厨", "好友"))
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(zone, locale).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis
    }
}
