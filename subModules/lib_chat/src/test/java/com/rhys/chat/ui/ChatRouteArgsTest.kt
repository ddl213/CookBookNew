package com.rhys.chat.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.campaign.common.constants.Constants.KEY_BUNDLE_CONVERSATION_ID
import com.campaign.common.constants.Constants.KEY_BUNDLE_PEER_AVATAR
import com.campaign.common.constants.Constants.KEY_BUNDLE_PEER_NICKNAME
import com.campaign.common.constants.Constants.KEY_BUNDLE_PEER_USER_ID
import com.rhys.chat.data.crypto.ChatCipher
import com.rhys.chat.domain.ChatConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 聊天详情路由参数 Bundle 读写：字段不丢失，非法/缺失 ID 明确为 0。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatRouteArgsTest {

    @Test
    fun `conversation args round trip keeps all fields`() {
        val conversation = ChatConversation(
            id = 101L,
            peerId = 2L,
            peerNickname = "林小厨",
            peerAvatar = "/avatar/2.png",
            lastMessagePreview = "周六见",
            updatedAt = 1_700_000_000_000L,
            updatedAtRaw = "2026-10-20T09:02:00Z"
        )

        val args = ChatUiSupport.conversationArgs(conversation)

        assertEquals(101L, args.getLong(KEY_BUNDLE_CONVERSATION_ID))
        assertEquals(2L, args.getLong(KEY_BUNDLE_PEER_USER_ID))
        assertEquals("林小厨", args.getString(KEY_BUNDLE_PEER_NICKNAME))
        assertEquals("/avatar/2.png", args.getString(KEY_BUNDLE_PEER_AVATAR))

        val read = ChatUiSupport.readConversationArgs(args)
        assertEquals(101L, read.conversationId)
        assertEquals(2L, read.peerUserId)
        assertEquals("林小厨", read.peerNickname)
        assertEquals("/avatar/2.png", read.peerAvatar)
        assertTrue(read.isValid)
    }

    @Test
    fun `deep link string values are parsed without loss`() {
        val args = Bundle().apply {
            putString(KEY_BUNDLE_CONVERSATION_ID, "101")
            putString(KEY_BUNDLE_PEER_USER_ID, "2")
            putString(KEY_BUNDLE_PEER_NICKNAME, "林小厨")
            putString(KEY_BUNDLE_PEER_AVATAR, "/avatar/2.png")
        }

        val read = ChatUiSupport.readConversationArgs(args)

        assertEquals(101L, read.conversationId)
        assertEquals(2L, read.peerUserId)
        assertEquals("林小厨", read.peerNickname)
        assertEquals("/avatar/2.png", read.peerAvatar)
        assertTrue(read.isValid)
    }

    @Test
    fun `missing or invalid ids become zero and invalid`() {
        val nullRead = ChatUiSupport.readConversationArgs(null)
        assertEquals(0L, nullRead.conversationId)
        assertEquals(0L, nullRead.peerUserId)
        assertNull(nullRead.peerNickname)
        assertNull(nullRead.peerAvatar)
        assertFalse(nullRead.hasConversationId)
        assertFalse(nullRead.hasPeerUserId)
        assertFalse(nullRead.isValid)

        val invalid = Bundle().apply {
            putString(KEY_BUNDLE_CONVERSATION_ID, "not-a-number")
            putString(KEY_BUNDLE_PEER_USER_ID, "-3")
        }
        val invalidRead = ChatUiSupport.readConversationArgs(invalid)
        assertEquals(0L, invalidRead.conversationId)
        assertEquals(0L, invalidRead.peerUserId)
        assertFalse(invalidRead.hasConversationId)
        assertFalse(invalidRead.hasPeerUserId)
        assertFalse(invalidRead.isValid)
    }

    @Test
    fun `unavailable nickname and avatar are omitted from args`() {
        val conversation = ChatConversation(
            id = 101L,
            peerId = 2L,
            peerNickname = ChatCipher.UNAVAILABLE_TEXT,
            peerAvatar = ChatCipher.UNAVAILABLE_TEXT,
            lastMessagePreview = null,
            updatedAt = 0L,
            updatedAtRaw = null
        )

        val read = ChatUiSupport.readConversationArgs(ChatUiSupport.conversationArgs(conversation))

        assertEquals(101L, read.conversationId)
        assertEquals(2L, read.peerUserId)
        assertNull(read.peerNickname)
        assertNull(read.peerAvatar)
        assertTrue(read.isValid)
    }

    @Test
    fun `either conversation id or peer id alone is valid`() {
        // 从消息列表进入：已有会话，peerUserId 可缺省。
        val conversationOnly = ChatUiSupport.readConversationArgs(Bundle().apply {
            putString(KEY_BUNDLE_CONVERSATION_ID, "101")
        })
        assertEquals(101L, conversationOnly.conversationId)
        assertEquals(0L, conversationOnly.peerUserId)
        assertTrue(conversationOnly.hasConversationId)
        assertFalse(conversationOnly.hasPeerUserId)
        assertTrue(conversationOnly.isValid)

        // 从好友主页进入：只有 peerUserId，由详情页调用 openConversation 创建会话。
        val peerOnly = ChatUiSupport.readConversationArgs(Bundle().apply {
            putString(KEY_BUNDLE_PEER_USER_ID, "2")
            putString(KEY_BUNDLE_PEER_NICKNAME, "林小厨")
        })
        assertEquals(0L, peerOnly.conversationId)
        assertEquals(2L, peerOnly.peerUserId)
        assertFalse(peerOnly.hasConversationId)
        assertTrue(peerOnly.hasPeerUserId)
        assertTrue(peerOnly.isValid)
        assertEquals("林小厨", peerOnly.peerNickname)

        // 两者都有：消息列表入口同时携带对方资料。
        val both = ChatUiSupport.readConversationArgs(Bundle().apply {
            putString(KEY_BUNDLE_CONVERSATION_ID, "101")
            putString(KEY_BUNDLE_PEER_USER_ID, "2")
            putString(KEY_BUNDLE_PEER_AVATAR, "/avatar/2.png")
        })
        assertTrue(both.hasConversationId)
        assertTrue(both.hasPeerUserId)
        assertTrue(both.isValid)
        assertEquals("/avatar/2.png", both.peerAvatar)

        // 两者都为 0：没有可用入口，必须判为非法。
        val neither = ChatUiSupport.readConversationArgs(null)
        assertFalse(neither.hasConversationId)
        assertFalse(neither.hasPeerUserId)
        assertFalse(neither.isValid)
    }

    @Test
    fun `detail entry prefers conversation id then peer id`() {
        // 两个 ID 同时存在时直接使用已有会话，不重复打开/创建。
        val both = ChatUiSupport.readConversationArgs(Bundle().apply {
            putString(KEY_BUNDLE_CONVERSATION_ID, "101")
            putString(KEY_BUNDLE_PEER_USER_ID, "2")
        })
        assertEquals(ConversationEntry.CONVERSATION, ChatUiSupport.conversationEntry(both))

        // 从消息列表进入：只有 conversationId。
        val conversationOnly = ChatUiSupport.readConversationArgs(Bundle().apply {
            putString(KEY_BUNDLE_CONVERSATION_ID, "101")
        })
        assertEquals(
            ConversationEntry.CONVERSATION,
            ChatUiSupport.conversationEntry(conversationOnly)
        )

        // 从好友主页进入：只有 peerUserId，详情页需要先 openConversation。
        val peerOnly = ChatUiSupport.readConversationArgs(Bundle().apply {
            putString(KEY_BUNDLE_PEER_USER_ID, "2")
        })
        assertEquals(ConversationEntry.PEER, ChatUiSupport.conversationEntry(peerOnly))

        // 两者都缺失/非法：不得发起任何请求。
        assertEquals(
            ConversationEntry.INVALID,
            ChatUiSupport.conversationEntry(ChatUiSupport.readConversationArgs(null))
        )
    }

    @Test
    fun `negative ids from deep link and bundle are normalized to zero`() {
        val deepLink = Bundle().apply {
            putParcelable(
                "android-support-nav:controller:deepLinkIntent",
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "cookbook://chat?key_conversation_id=-3&key_peer_user_id=-4"
                    )
                )
            )
        }
        val deepLinkRead = ChatUiSupport.readConversationArgs(deepLink)
        assertEquals(0L, deepLinkRead.conversationId)
        assertEquals(0L, deepLinkRead.peerUserId)
        assertFalse(deepLinkRead.isValid)

        // Bundle 中的 Long 负数同样归零，不按负数继续请求。
        val bundleLong = ChatUiSupport.readConversationArgs(Bundle().apply {
            putLong(KEY_BUNDLE_CONVERSATION_ID, -3L)
            putLong(KEY_BUNDLE_PEER_USER_ID, -4L)
        })
        assertEquals(0L, bundleLong.conversationId)
        assertEquals(0L, bundleLong.peerUserId)
        assertFalse(bundleLong.isValid)

        // 正数 deep link 参数仍能正常读取，未被归零逻辑误伤。
        val positiveDeepLink = Bundle().apply {
            putParcelable(
                "android-support-nav:controller:deepLinkIntent",
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("cookbook://chat?key_conversation_id=101&key_peer_user_id=3")
                )
            )
        }
        val positiveRead = ChatUiSupport.readConversationArgs(positiveDeepLink)
        assertEquals(101L, positiveRead.conversationId)
        assertEquals(3L, positiveRead.peerUserId)
        assertTrue(positiveRead.isValid)
    }
}
