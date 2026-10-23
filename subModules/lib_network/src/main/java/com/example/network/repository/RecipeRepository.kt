package com.example.network.repository

import com.example.network.ApiClient
import com.example.network.RecipeMediaRules
import com.example.network.bean.PageResponse
import com.example.network.bean.RecipeDto
import com.example.network.bean.RecipeWriteBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 菜谱数据访问层：我的菜谱、详情、创建/更新/删除与图片上传。
 *
 * 只做参数归一化、上传约束和异常转换；权限与草稿可见性由后端最终判定。
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

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50
        const val STATUS_PUBLISHED = "published"
        const val STATUS_DRAFT = "draft"
        const val STATUS_ALL = "all"
        private val ALLOWED_STATUSES = setOf(STATUS_PUBLISHED, STATUS_DRAFT, STATUS_ALL)
    }
}
