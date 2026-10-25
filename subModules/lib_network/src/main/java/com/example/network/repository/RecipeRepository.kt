package com.example.network.repository

import com.example.network.ApiClient
import com.example.network.RecipeMediaRules
import com.example.network.bean.CommentCreateBody
import com.example.network.bean.CommentDto
import com.example.network.bean.PageResponse
import com.example.network.bean.RecipeDto
import com.example.network.bean.RecipeWriteBody
import com.example.network.bean.ShareBody
import com.example.network.bean.ShareResultDto
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 菜谱数据访问层：我的菜谱、详情、创建/更新/删除、图片上传与详情互动。
 *
 * 只做参数归一化、上传约束和异常转换；权限、草稿可见性与收藏/分享资格由后端最终判定。
 * 删除是 204 空响应，必须通过 requireSuccessful 校验，不能把失败当成成功。
 */
class RecipeRepository {

    suspend fun listMine(
        status: String,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PageResponse<RecipeDto> {
        require(status in ALLOWED_STATUSES) { "status must be published, draft or all" }
        val safePage = page.coerceAtLeast(1)
        val safePageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        return callRelationApi {
            ApiClient.recipeApi.listMine(status, safePage, safePageSize)
        }
    }

    /**
     * 公开菜谱列表（匿名可读）。category/query 会先 trim，空白转为 null。
     * 推荐/热门等“最新排序”口径由后端默认排序决定，客户端不自行实现推荐算法。
     */
    suspend fun listPublic(
        category: String? = null,
        query: String? = null,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PageResponse<RecipeDto> {
        val safePage = normalizedPage(page)
        val safePageSize = normalizedPageSize(pageSize)
        return callRelationApi {
            ApiClient.recipeApi.listPublic(
                category = normalizedFilter(category),
                q = normalizedFilter(query),
                page = safePage,
                pageSize = safePageSize
            )
        }
    }

    /** 好友动态：需要登录；未登录时由页面阻止请求，这里不吞掉 401。 */
    suspend fun listFeed(
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PageResponse<RecipeDto> {
        val safePage = normalizedPage(page)
        val safePageSize = normalizedPageSize(pageSize)
        return callRelationApi {
            ApiClient.recipeApi.listFeed(safePage, safePageSize)
        }
    }

    suspend fun get(id: Long): RecipeDto {
        require(id > 0) { "recipe id must be positive" }
        return callRelationApi { ApiClient.recipeApi.get(id) }
    }

    suspend fun create(body: RecipeWriteBody): RecipeDto {
        return callRelationApi { ApiClient.recipeApi.create(body) }
    }

    suspend fun update(id: Long, body: RecipeWriteBody): RecipeDto {
        require(id > 0) { "recipe id must be positive" }
        return callRelationApi { ApiClient.recipeApi.update(id, body) }
    }

    /** 204 同样必须显式校验；4xx/5xx 会转换为可展示的 AuthException。 */
    suspend fun delete(id: Long) {
        require(id > 0) { "recipe id must be positive" }
        callRelationApi {
            ApiClient.recipeApi.delete(id).requireSuccessful()
        }
    }

    /**
     * 上传图片并返回后端原始路径（/uploads/...）。
     * 调用方负责在展示前用 NetworkConfig.resolveMediaUrl 解析。
     * 大小与 MIME 在客户端先做一次防御性校验，后端仍是最终校验方。
     */
    suspend fun uploadImage(
        bytes: ByteArray,
        mimeType: String,
        fileName: String
    ): String {
        require(bytes.isNotEmpty()) { "image bytes must not be empty" }
        require(RecipeMediaRules.isAllowedMimeType(mimeType)) { "unsupported image mime type" }
        require(RecipeMediaRules.isWithinSizeLimit(bytes.size.toLong())) {
            "image exceeds upload size limit"
        }
        val safeMime = mimeType.trim().lowercase()
        val safeName = RecipeMediaRules.uploadFileName(fileName, safeMime)
        return callRelationApi {
            val mediaType = safeMime.toMediaTypeOrNull()
            val body = bytes.toRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", safeName, body)
            val url = ApiClient.uploadApi.upload(part).url.trim()
            check(url.isNotEmpty()) { "upload response url is empty" }
            url
        }
    }

    suspend fun listComments(
        recipeId: Long,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): PageResponse<CommentDto> {
        require(recipeId > 0) { "recipe id must be positive" }
        val safePage = normalizedPage(page)
        val safePageSize = normalizedPageSize(pageSize)
        return callRelationApi {
            ApiClient.socialApi.listComments(recipeId, safePage, safePageSize)
        }
    }

    /** 客户端先 trim 并校验 1..1000，后端仍是最终校验方。 */
    suspend fun createComment(recipeId: Long, content: String): CommentDto {
        require(recipeId > 0) { "recipe id must be positive" }
        val safeContent = content.trim()
        require(safeContent.isNotEmpty()) { "comment content must not be blank" }
        require(safeContent.length <= MAX_COMMENT_LENGTH) {
            "comment content must not exceed $MAX_COMMENT_LENGTH characters"
        }
        return callRelationApi {
            ApiClient.socialApi.createComment(recipeId, CommentCreateBody(safeContent))
        }
    }

    /** 返回后端确认的收藏状态；调用方据此更新按钮，不能自行假定成功。 */
    suspend fun setCollected(recipeId: Long, collected: Boolean): Boolean {
        require(recipeId > 0) { "recipe id must be positive" }
        return callRelationApi {
            val state = if (collected) {
                ApiClient.socialApi.addCollection(recipeId)
            } else {
                ApiClient.socialApi.removeCollection(recipeId)
            }
            state.collected
        }
    }

    suspend fun share(recipeId: Long, toUserId: Long): ShareResultDto {
        require(recipeId > 0) { "recipe id must be positive" }
        require(toUserId > 0) { "toUserId must be positive" }
        return callRelationApi {
            ApiClient.socialApi.share(recipeId, ShareBody(toUserId))
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
        const val MAX_COMMENT_LENGTH = 1000
        const val STATUS_PUBLISHED = "published"
        const val STATUS_DRAFT = "draft"
        const val STATUS_ALL = "all"
        private val ALLOWED_STATUSES = setOf(STATUS_PUBLISHED, STATUS_DRAFT, STATUS_ALL)

        /** 分页参数归一化独立暴露，便于纯 JVM 单测覆盖边界。 */
        internal fun normalizedPage(page: Int): Int = page.coerceAtLeast(1)

        internal fun normalizedPageSize(pageSize: Int): Int = pageSize.coerceIn(1, MAX_PAGE_SIZE)

        /** 查询过滤值归一化：null/空白转 null，其余去掉首尾空格。 */
        internal fun normalizedFilter(value: String?): String? =
            value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
