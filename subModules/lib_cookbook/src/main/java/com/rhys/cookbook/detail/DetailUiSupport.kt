package com.rhys.cookbook.detail

import android.widget.ImageView
import android.widget.TextView
import com.android.common.ext.gone
import com.android.common.ext.load
import com.android.common.ext.visible
import com.example.network.NetworkConfig

/**
 * 详情页与分享弹窗共用的头像/时间展示。
 *
 * lib_cookbook 不依赖 lib_relation，因此保留本模块内的最小实现，避免跨模块 internal 依赖。
 */
internal fun bindDetailAvatar(
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

/** 取昵称首字，避免截断 emoji 等代理对字符。 */
internal fun firstCharacter(text: String): String {
    if (text.isEmpty()) return ""
    val end = if (Character.isHighSurrogate(text[0]) && text.length > 1) 2 else 1
    return text.substring(0, end)
}

/** 后端返回 ISO 时间；仅做轻量展示，不引入额外日期库。 */
internal fun formatCommentTime(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return ""
    return value.take(16).replace('T', ' ')
}
