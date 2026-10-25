package com.rhys.chat.ui

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import com.android.common.ext.gone
import com.android.common.ext.load
import com.android.common.ext.visible
import com.campaign.common.constants.Constants.KEY_BUNDLE_CONVERSATION_ID
import com.campaign.common.constants.Constants.KEY_BUNDLE_PEER_AVATAR
import com.campaign.common.constants.Constants.KEY_BUNDLE_PEER_NICKNAME
import com.campaign.common.constants.Constants.KEY_BUNDLE_PEER_USER_ID
import com.example.network.NetworkConfig
import com.marky.route.api.NRoute
import com.rhys.chat.data.ChatRealtimeState
import com.rhys.chat.data.crypto.ChatCipher
import com.rhys.chat.domain.ChatConversation
import com.rhys.chat.domain.ChatMessageStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 聊天列表与预订通知页共用的展示规则。
 *
 * 只处理安全兜底、时间格式化、头像绑定和路由参数读写；不访问数据源、
 * 不修改 Repository，也不推断服务端已读/同步状态。昵称、预览与时间文案
 * 由调用方从资源传入，便于列表页、通知页和后续聊天详情页保持一致。
 */
internal object ChatUiSupport {

    /**
     * 列表时间：今天显示 HH:mm，昨天显示传入的“昨天”文案，更早显示 yyyy-MM-dd。
     * [millis] <= 0 视为无效时间并返回 null，页面不应展示时间。
     */
    fun formatListTime(
        millis: Long,
        yesterdayLabel: String,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): String? {
        if (millis <= 0L) return null
        val target = Calendar.getInstance(zone, locale).apply { timeInMillis = millis }
        val current = Calendar.getInstance(zone, locale).apply {
            timeInMillis = if (now > 0L) now else System.currentTimeMillis()
        }
        return when {
            isSameDay(target, current) -> formatDate(millis, "HH:mm", zone, locale)
            isYesterday(target, current) -> yesterdayLabel
            else -> formatDate(millis, "yyyy-MM-dd", zone, locale)
        }
    }

    /**
     * 聊天气泡时间：今天显示 HH:mm，其他日期显示 yyyy-MM-dd HH:mm。
     * [millis] <= 0 视为无效时间并返回 null，页面不展示时间行。
     */
    fun formatMessageTime(
        millis: Long,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): String? {
        if (millis <= 0L) return null
        val target = Calendar.getInstance(zone, locale).apply { timeInMillis = millis }
        val current = Calendar.getInstance(zone, locale).apply {
            timeInMillis = if (now > 0L) now else System.currentTimeMillis()
        }
        return if (isSameDay(target, current)) {
            formatDate(millis, "HH:mm", zone, locale)
        } else {
            formatDate(millis, "yyyy-MM-dd HH:mm", zone, locale)
        }
    }

    /** 是否为自己发送的消息；未确定当前用户时一律按对方处理，避免误显发送状态。 */
    fun isOwnMessage(senderId: Long, currentUserId: Long): Boolean =
        currentUserId > 0L && senderId == currentUserId

    /**
     * 发送状态标签：SENDING/FAILED 展示传入文案，SENT 返回 null（不展示已读/送达）。
     */
    fun messageStatusLabel(
        status: ChatMessageStatus,
        sendingLabel: String,
        failedLabel: String
    ): String? = when (status) {
        ChatMessageStatus.SENDING -> sendingLabel
        ChatMessageStatus.FAILED -> failedLabel
        ChatMessageStatus.SENT -> null
    }

    /**
     * 实时状态横幅文案：UNAVAILABLE/CONNECTING/DISCONNECTED 展示对应提示，
     * CONNECTED 返回 null。本期不会伪造已连接状态。
     */
    fun realtimeBannerText(
        state: ChatRealtimeState,
        unavailableText: String,
        disconnectedText: String,
        connectingText: String
    ): String? = when (state) {
        ChatRealtimeState.UNAVAILABLE -> unavailableText
        ChatRealtimeState.DISCONNECTED -> disconnectedText
        ChatRealtimeState.CONNECTING -> connectingText
        ChatRealtimeState.CONNECTED -> null
    }

    /** 详情页入口分支：会话 ID 优先，其次好友 ID；两者都非法时不发起请求。 */
    fun conversationEntry(args: ConversationRouteArgs): ConversationEntry = when {
        args.hasConversationId -> ConversationEntry.CONVERSATION
        args.hasPeerUserId -> ConversationEntry.PEER
        else -> ConversationEntry.INVALID
    }

    /** 昵称兜底：空白或解密失败占位都展示传入的安全昵称。 */
    fun displayNickname(raw: String?, fallback: String): String {
        val text = raw?.trim().orEmpty()
        return if (text.isEmpty() || isUnavailable(text)) fallback else text
    }

    /** 最后消息预览兜底：仅空白视为“暂无消息”；解密失败保留安全占位。 */
    fun displayPreview(raw: String?, emptyLabel: String): String {
        val text = raw?.trim().orEmpty()
        return if (text.isEmpty()) emptyLabel else text
    }

    /** 通知标题兜底：空白或解密失败占位使用安全标题。 */
    fun displayNotificationTitle(raw: String?, fallback: String): String {
        val text = raw?.trim().orEmpty()
        return if (text.isEmpty() || isUnavailable(text)) fallback else text
    }

    /** 通知正文：解密失败保留安全占位；空白返回空串由页面隐藏正文。 */
    fun displayNotificationBody(raw: String?): String = raw?.trim().orEmpty()

    /** 头像占位首字；昵称为空或不可用时取安全昵称的首字，不截断 emoji 代理对。 */
    fun avatarInitial(nickname: String?, nicknameFallback: String): String =
        firstCharacter(displayNickname(nickname, nicknameFallback))

    /** 取首个 Unicode 码点，避免把 emoji 等代理对截成两个乱码字符。 */
    fun firstCharacter(text: String): String {
        if (text.isEmpty()) return ""
        val codePoint = text.codePointAt(0)
        return String(Character.toChars(codePoint))
    }

    /** 可安全交给图片加载器的头像地址；空白和解密失败占位都返回 null。 */
    fun avatarUrlOrNull(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        return text.takeIf { it.isNotEmpty() && !isUnavailable(it) }
    }

    /** 解密失败占位只用于展示，不能当作昵称、预览或头像 URL 使用。 */
    fun isUnavailable(raw: String?): Boolean =
        raw?.trim() == ChatCipher.UNAVAILABLE_TEXT

    /**
     * 绑定会话头像：有可用 URL 时用 Glide；否则显示安全昵称首字圆形占位。
     */
    fun bindAvatar(
        imageView: ImageView,
        fallbackView: TextView,
        nickname: String?,
        avatarUrl: String?,
        nicknameFallback: String
    ) {
        val url = NetworkConfig.resolveMediaUrl(avatarUrlOrNull(avatarUrl))
        if (url == null) {
            imageView.gone()
            fallbackView.visible()
            fallbackView.text = avatarInitial(nickname, nicknameFallback)
        } else {
            fallbackView.gone()
            imageView.visible()
            imageView.load(url)
        }
    }

    /**
     * 构建聊天详情路由参数。
     *
     * 昵称/头像缺省或不可用时直接省略 key，不能写入 null：NRoute 会把 Bundle
     * 值转成 query 参数，null 会变成字面量 "null" 并被详情页误当作真实昵称/URL。
     */
    fun conversationArgs(conversation: ChatConversation): Bundle = Bundle().apply {
        putLong(KEY_BUNDLE_CONVERSATION_ID, conversation.id)
        putLong(KEY_BUNDLE_PEER_USER_ID, conversation.peerId)
        conversation.peerNickname
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !isUnavailable(it) }
            ?.let { putString(KEY_BUNDLE_PEER_NICKNAME, it) }
        avatarUrlOrNull(conversation.peerAvatar)
            ?.let { putString(KEY_BUNDLE_PEER_AVATAR, it) }
    }

    /**
     * 读取聊天详情路由参数。
     *
     * 兼容 Bundle 中的 Long 和 deep link query 的 String；会话/用户 ID 缺失、
     * 非数字或 <= 0 时返回 0，调用方必须视为非法参数，不得按 0 继续请求。
     */
    fun readConversationArgs(arguments: Bundle?): ConversationRouteArgs {
        if (arguments == null) return ConversationRouteArgs.EMPTY
        return ConversationRouteArgs(
            conversationId = readLong(arguments, KEY_BUNDLE_CONVERSATION_ID),
            peerUserId = readLong(arguments, KEY_BUNDLE_PEER_USER_ID),
            peerNickname = readString(arguments, KEY_BUNDLE_PEER_NICKNAME),
            peerAvatar = readString(arguments, KEY_BUNDLE_PEER_AVATAR)
        )
    }

    @Suppress("DEPRECATION")
    private fun readLong(arguments: Bundle, key: String): Long {
        val direct = when (val value = arguments.get(key)) {
            is Long -> value
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull() ?: 0L
            else -> 0L
        }
        if (direct > 0L) return direct
        // NRoute 以 Uri query 传参时，值不在 Bundle 类型化字段中，回退读取原始 deep link。
        // 负数与非数字同样视为非法参数归零，调用方只按 > 0 判断是否可请求。
        return NRoute.getArgument(arguments, key)
            .trim()
            .toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: 0L
    }

    @Suppress("DEPRECATION")
    private fun readString(arguments: Bundle, key: String): String? {
        val direct = (arguments.get(key) as? String)?.trim()
        if (!direct.isNullOrEmpty()) return direct
        return NRoute.getArgument(arguments, key).trim().takeIf { it.isNotEmpty() }
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean =
        first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(target: Calendar, now: Calendar): Boolean {
        val yesterday = now.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        return isSameDay(target, yesterday)
    }

    private fun formatDate(
        millis: Long,
        pattern: String,
        zone: TimeZone,
        locale: Locale
    ): String = SimpleDateFormat(pattern, locale).apply {
        timeZone = zone
    }.format(Date(millis))
}

/**
 * 聊天详情路由参数；会话/用户 ID 为 0 表示缺失或非法，负数已由读取入口归零。
 *
 * 详情页允许两种合法入口：
 * - 从消息列表进入：带 conversationId，peerUserId 可缺省；
 * - 从好友主页进入：只带 peerUserId，由详情页调用 openConversation 创建会话。
 * 因此 [isValid] 表示“至少一个可用 ID”，不能要求两者同时存在。
 */
internal data class ConversationRouteArgs(
    val conversationId: Long,
    val peerUserId: Long,
    val peerNickname: String?,
    val peerAvatar: String?
) {
    val hasConversationId: Boolean
        get() = conversationId > 0L

    val hasPeerUserId: Boolean
        get() = peerUserId > 0L

    val isValid: Boolean
        get() = hasConversationId || hasPeerUserId

    companion object {
        val EMPTY = ConversationRouteArgs(0L, 0L, null, null)
    }
}

/** 详情页打开方式：已有会话、仅好友 ID（需先创建/打开会话）、非法参数。 */
internal enum class ConversationEntry {
    CONVERSATION,
    PEER,
    INVALID
}
