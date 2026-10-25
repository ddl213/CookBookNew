package com.rhys.chat.ui

import com.rhys.chat.data.ChatRealtimeState
import com.rhys.chat.domain.ChatMessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** 聊天详情展示规则：消息归属、时间、发送状态与实时不可用提示。 */
class ChatDetailUiSupportTest {

    private val zone = TimeZone.getTimeZone("Asia/Shanghai")
    private val locale = Locale.US

    @Test
    fun `own message only matches positive current user`() {
        assertTrue(ChatUiSupport.isOwnMessage(senderId = 7L, currentUserId = 7L))
        assertFalse(ChatUiSupport.isOwnMessage(senderId = 7L, currentUserId = 8L))
        assertFalse(ChatUiSupport.isOwnMessage(senderId = 0L, currentUserId = 0L))
        assertFalse(ChatUiSupport.isOwnMessage(senderId = 0L, currentUserId = 7L))
    }

    @Test
    fun `message time today uses hour and minute`() {
        val now = millis(2026, 10, 25, 15, 30)
        val today = millis(2026, 10, 25, 9, 5)

        assertEquals("09:05", ChatUiSupport.formatMessageTime(today, now, zone, locale))
    }

    @Test
    fun `message time on other days includes date`() {
        val now = millis(2026, 10, 25, 15, 30)
        val yesterday = millis(2026, 10, 24, 23, 59)
        val earlier = millis(2026, 10, 1, 8, 0)

        assertEquals(
            "2026-10-24 23:59",
            ChatUiSupport.formatMessageTime(yesterday, now, zone, locale)
        )
        assertEquals(
            "2026-10-01 08:00",
            ChatUiSupport.formatMessageTime(earlier, now, zone, locale)
        )
    }

    @Test
    fun `message time invalid values return null`() {
        val now = millis(2026, 10, 25, 15, 30)

        assertNull(ChatUiSupport.formatMessageTime(0L, now, zone, locale))
        assertNull(ChatUiSupport.formatMessageTime(-1L, now, zone, locale))
    }

    @Test
    fun `status label only shows in flight or failed`() {
        assertEquals(
            "发送中…",
            ChatUiSupport.messageStatusLabel(
                ChatMessageStatus.SENDING,
                sendingLabel = "发送中…",
                failedLabel = "发送失败，点击重试"
            )
        )
        assertEquals(
            "发送失败，点击重试",
            ChatUiSupport.messageStatusLabel(
                ChatMessageStatus.FAILED,
                sendingLabel = "发送中…",
                failedLabel = "发送失败，点击重试"
            )
        )
        assertNull(
            ChatUiSupport.messageStatusLabel(
                ChatMessageStatus.SENT,
                sendingLabel = "发送中…",
                failedLabel = "发送失败，点击重试"
            )
        )
    }

    @Test
    fun `realtime banner hides only when connected`() {
        val unavailable = "实时消息暂不可用，发送后可下拉刷新/重试"
        val disconnected = "实时消息已断开，可下拉刷新"
        val connecting = "正在连接实时消息…"

        assertEquals(
            unavailable,
            ChatUiSupport.realtimeBannerText(
                ChatRealtimeState.UNAVAILABLE,
                unavailable,
                disconnected,
                connecting
            )
        )
        assertEquals(
            disconnected,
            ChatUiSupport.realtimeBannerText(
                ChatRealtimeState.DISCONNECTED,
                unavailable,
                disconnected,
                connecting
            )
        )
        assertEquals(
            connecting,
            ChatUiSupport.realtimeBannerText(
                ChatRealtimeState.CONNECTING,
                unavailable,
                disconnected,
                connecting
            )
        )
        assertNull(
            ChatUiSupport.realtimeBannerText(
                ChatRealtimeState.CONNECTED,
                unavailable,
                disconnected,
                connecting
            )
        )
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(zone, locale).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis
    }
}
