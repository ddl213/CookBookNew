package com.example.network.repository

import com.example.network.ApiClient
import com.example.network.bean.PageResponse
import com.example.network.bean.RecipeDto
import com.example.network.bean.UserListItemDto
import com.example.network.bean.UserProfileDto

/**
 * 用户数据访问层：主页、搜索、用户可见菜谱。
 * 只做接口调用与分页参数归一化，不包含 UI 或权限判定；后端是最终校验方。
 */
class UserRepository {

    suspend fun profile(userId: Long): UserProfileDto {
        require(userId > 0) { "userId must be positive" }
        return callRelationApi { ApiClient.userApi.profile(userId) }
    }

    suspend fun search(
        keyword: String,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PageResponse<UserListItemDto> {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val query = keyword.trim().takeIf { it.isNotEmpty() }
        return callRelationApi {
            ApiClient.userApi.search(query, safePage, safePageSize)
        }
    }

    suspend fun recipes(
        userId: Long,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PageResponse<RecipeDto> {
        require(userId > 0) { "userId must be positive" }
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        return callRelationApi {
            ApiClient.userApi.recipes(userId, safePage, safePageSize)
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
    }
}
