package com.example.network.bean

/**
 * 统一游标分页响应：{ items, nextCursor }。
 *
 * nextCursor 为 null 或空白表示没有更早/下一页；游标对客户端不透明，
 * 原样回传到下一次请求即可，不能自行解析或拼接。
 */
data class CursorPage<T>(
    val items: List<T> = emptyList(),
    val nextCursor: String? = null
) {
    val hasMore: Boolean
        get() = !nextCursor.isNullOrBlank()
}
