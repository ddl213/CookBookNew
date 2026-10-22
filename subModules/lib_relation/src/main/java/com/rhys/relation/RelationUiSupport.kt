package com.rhys.relation

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.common.ext.gone
import com.android.common.ext.load
import com.android.common.ext.visible
import com.campaign.common.constants.Constants.KEY_USER_ID
import com.campaign.common.constants.RoutePath.PAGE_LOGIN
import com.campaign.common.constants.RoutePath.PAGE_USER_PROFILE
import com.example.network.NetworkConfig
import com.marky.route.api.NRoute

/**
 * 用户/好友页面的公共 UI 小工具，避免重复处理头像占位与路由参数。
 */
internal fun bindRelationAvatar(
    imageView: ImageView,
    fallbackView: TextView,
    displayName: String,
    avatarUrl: String?
) {
    val url = NetworkConfig.resolveMediaUrl(avatarUrl)
    if (url == null) {
        imageView.gone()
        fallbackView.visible()
        fallbackView.text = firstCharacter(displayName)
    } else {
        fallbackView.gone()
        imageView.visible()
        imageView.load(url)
    }
}

/** 取昵称首字作为头像占位，避免截断 emoji 等代理对字符。 */
internal fun firstCharacter(text: String): String {
    if (text.isEmpty()) return ""
    val end = if (Character.isHighSurrogate(text[0]) && text.length > 1) 2 else 1
    return text.substring(0, end)
}

internal fun openUserProfile(fragment: Fragment, userId: Long) {
    if (userId <= 0) return
    val args = Bundle().apply { putLong(KEY_USER_ID, userId) }
    NRoute.withNavController(fragment.findNavController())
        .deepLink(PAGE_USER_PROFILE)
        .args(args)
        .go()
}

internal fun openLoginPage(fragment: Fragment): Boolean {
    return runCatching {
        NRoute.withNavController(fragment.findNavController())
            .deepLink(PAGE_LOGIN)
            .go()
    }.isSuccess
}

/**
 * 读取用户 ID 路由参数。
 * NRoute 以 Uri query 传参，优先读取已解析的 Bundle，再回退到原始 deep link。
 */
internal fun readUserIdArgument(arguments: Bundle?): Long {
    if (arguments == null) return 0L
    val direct = arguments.getLong(KEY_USER_ID, 0L)
    if (direct > 0L) return direct
    return NRoute.getArgument(arguments, KEY_USER_ID).toLongOrNull() ?: 0L
}
