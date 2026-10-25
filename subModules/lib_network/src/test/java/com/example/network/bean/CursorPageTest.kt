package com.example.network.bean

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 游标分页与聊天 DTO 的 Gson 解析。 */
class CursorPageTest {

    private val gson = Gson()

    @Test
    fun `message page parses items and next cursor`() {
        val json = """
            {
              "items": [
                {
                  "id": 11,
                  "conversationId": 101,
                  "clientMessageId": "client-1",
                  "senderId": 2,
                  "receiverId": 1,
                  "type": "TEXT",
                  "content": "周六见",
                  "createdAt": "2026-10-20T09:02:00Z"
                }
              ],
              "nextCursor": "10"
            }
        """.trimIndent()
        val type = object : TypeToken<CursorPage<ChatMessageDto>>() {}.type
        val page: CursorPage<ChatMessageDto> = gson.fromJson(json, type)

        assertEquals(1, page.items.size)
        assertEquals(101L, page.items[0].conversationId)
        assertEquals("client-1", page.items[0].clientMessageId)
        assertEquals(ChatMessageDto.TYPE_TEXT, page.items[0].type)
        assertEquals("10", page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun `missing fields use safe defaults`() {
        val type = object : TypeToken<CursorPage<ChatConversationDto>>() {}.type
        val page: CursorPage<ChatConversationDto> =
            gson.fromJson("""{"items":[{"id":7}]}""", type)

        assertEquals(1, page.items.size)
        assertEquals(7L, page.items[0].id)
        assertNull(page.items[0].peer)
        assertNull(page.items[0].lastMessage)
        assertNull(page.nextCursor)
        assertFalse(page.hasMore)
    }
}
