package com.example.network.repository

import com.example.network.ApiClient
import com.example.network.bean.FriendDto
import com.example.network.bean.FriendRequestCreateBody
import com.example.network.bean.FriendRequestDto
import com.example.network.bean.PageResponse

/** 好友申请列表方向。 */
enum class FriendRequestDirection(val apiValue: String) {
    INCOMING("incoming"),
    OUTGOING("outgoing")
}

/**
 * 好友数据访问层：好友列表、申请发送/查询/接受/拒绝、解除好友。
 * 不缓存状态；每次操作后由页面重新拉取以保证与服务端一致。
 */
class FriendRepository {

    suspend fun listFriends(
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PageResponse<FriendDto> {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        return callRelationApi {
            ApiClient.friendApi.listFriends(safePage, safePageSize)
        }
    }

    suspend fun sendRequest(toUserId: Long, message: String? = null): FriendRequestDto {
        require(toUserId > 0) { "toUserId must be positive" }
        val body = FriendRequestCreateBody(
            toUserId = toUserId,
            message = message?.trim()?.takeIf { it.isNotEmpty() }
        )
        return callRelationApi { ApiClient.friendApi.sendRequest(body) }
    }

    /**
     * @param status all 用于同时展示待处理与已处理记录，页面按 status 决定是否显示操作按钮。
     */
    suspend fun listRequests(
        direction: FriendRequestDirection,
        status: String = STATUS_ALL,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PageResponse<FriendRequestDto> {
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        return callRelationApi {
            ApiClient.friendApi.listRequests(
                direction = direction.apiValue,
                status = status,
                page = safePage,
                pageSize = safePageSize
            )
        }
    }

    suspend fun acceptRequest(requestId: Long): FriendRequestDto {
        require(requestId > 0) { "requestId must be positive" }
        return callRelationApi { ApiClient.friendApi.acceptRequest(requestId) }
    }

    suspend fun rejectRequest(requestId: Long): FriendRequestDto {
        require(requestId > 0) { "requestId must be positive" }
        return callRelationApi { ApiClient.friendApi.rejectRequest(requestId) }
    }

    /** 解除好友；后端返回 204，无响应体。非 2xx 必须抛错，避免失败被当成成功。 */
    suspend fun deleteFriend(userId: Long) {
        require(userId > 0) { "userId must be positive" }
        callRelationApi {
            ApiClient.friendApi.deleteFriend(userId).requireSuccessful()
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
        const val STATUS_PENDING = "pending"
        const val STATUS_ALL = "all"
    }
}
