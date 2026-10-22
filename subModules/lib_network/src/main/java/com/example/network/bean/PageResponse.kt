package com.example.network.bean

/**
 * CookBookService 分页响应：{ items, page, pageSize, total }。
 *
 * page 从 1 开始；pageSize 由后端限制为 1..50。
 */
data class PageResponse<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val total: Int = 0
) {
    /** 是否还有下一页；用于列表分页加载，不替代后端校验。 */
    val hasMore: Boolean
        get() = page * pageSize < total
}
