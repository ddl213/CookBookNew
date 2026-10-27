package com.example.network.repository

import com.example.network.ApiClient
import com.example.network.bean.PageResponse
import com.example.network.bean.ProfileUpdateBody
import com.example.network.bean.RecipeDto
import com.example.network.bean.UserDto
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

    /**
     * 更新当前用户资料。三个参数都为 null 表示没有修改，直接拒绝。
     * 归一化在访问网络前完成，后端仍是最终校验方。
     */
    suspend fun updateProfile(
        nickname: String? = null,
        avatar: String? = null,
        bio: String? = null
    ): UserDto {
        val body = normalizedProfile(nickname, avatar, bio)
        return callRelationApi {
            ApiClient.userApi.updateProfile(body)
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
        const val MAX_NICKNAME_LENGTH = 32
        const val MAX_BIO_LENGTH = 500

        /**
         * 资料更新参数归一化：先 trim 再校验，便于纯 JVM 单测覆盖边界。
         * nickname 提供时必须 1..32；avatar 提供时不得为空白；
         * bio 最长 500，允许空串表示清空。
         */
        internal fun normalizedProfile(
            nickname: String? = null,
            avatar: String? = null,
            bio: String? = null
        ): ProfileUpdateBody {
            val safeNickname = nickname?.trim()
            if (safeNickname != null) {
                require(safeNickname.length in 1..MAX_NICKNAME_LENGTH) {
                    "nickname must be 1..$MAX_NICKNAME_LENGTH characters"
                }
            }
            val safeAvatar = avatar?.trim()
            if (safeAvatar != null) {
                require(safeAvatar.isNotEmpty()) { "avatar must not be blank when provided" }
            }
            val safeBio = bio?.trim()
            if (safeBio != null) {
                require(safeBio.length <= MAX_BIO_LENGTH) {
                    "bio must not exceed $MAX_BIO_LENGTH characters"
                }
            }
            require(safeNickname != null || safeAvatar != null || safeBio != null) {
                "at least one profile field is required"
            }
            return ProfileUpdateBody(
                nickname = safeNickname,
                avatar = safeAvatar,
                bio = safeBio
            )
        }
    }
}
