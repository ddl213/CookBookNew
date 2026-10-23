package com.example.network

/**
 * 菜谱图片的上传约束，供表单预校验与 Repository 双重校验复用。
 *
 * 与后端 /uploads 约定保持一致：jpeg / png / webp，单张不超过 5 MiB。
 */
object RecipeMediaRules {

    const val MAX_IMAGE_BYTES: Long = 5L * 1024 * 1024

    val ALLOWED_MIME_TYPES: Set<String> = setOf(
        "image/jpeg",
        "image/png",
        "image/webp"
    )

    /** mime 忽略大小写；null/空白视为不支持。 */
    fun isAllowedMimeType(mimeType: String?): Boolean {
        val normalized = mimeType?.trim()?.lowercase().orEmpty()
        return normalized.isNotEmpty() && normalized in ALLOWED_MIME_TYPES
    }

    /** 0 字节视为无效；大于上限视为超限。 */
    fun isWithinSizeLimit(size: Long): Boolean = size in 1..MAX_IMAGE_BYTES

    /**
     * 部分内容提供方不返回 MIME 时，按文件名扩展名兜底。
     * 仍无法识别时返回 null，调用方应提示用户重新选择。
     */
    fun mimeTypeForFileName(fileName: String?): String? {
        val extension = fileName
            ?.substringAfterLast('.', "")
            ?.trim()
            ?.lowercase()
            .orEmpty()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> null
        }
    }

    /** 只保留文件名本身，避免把路径写入 multipart 文件名。 */
    fun uploadFileName(displayName: String?, mimeType: String): String {
        val cleaned = displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            .orEmpty()
        if (cleaned.isNotEmpty()) return cleaned
        return when (mimeType.lowercase()) {
            "image/png" -> "recipe.png"
            "image/webp" -> "recipe.webp"
            else -> "recipe.jpg"
        }
    }
}
