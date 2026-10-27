package com.example.network

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/**
 * 系统照片选择器返回 Uri 的只读工具。
 *
 * 只负责读取 MIME、文件名/大小与字节内容，不做业务校验；
 * 校验规则仍由调用方配合 [RecipeMediaRules] 完成。
 * 所有方法都可能执行磁盘/内容提供方 IO，调用方应在后台线程调用。
 */
object PickedImageReader {

    data class Meta(val fileName: String?, val size: Long?)

    /**
     * 优先使用内容提供方声明的 MIME；缺失或不支持时按文件名扩展名兜底。
     * 仍无法识别时返回 null，调用方应提示用户重新选择。
     */
    fun resolveMimeType(
        resolver: ContentResolver,
        uri: Uri,
        fileName: String?
    ): String? {
        val declared = runCatching { resolver.getType(uri) }.getOrNull()
        if (RecipeMediaRules.isAllowedMimeType(declared)) {
            return declared?.trim()?.lowercase()
        }
        return RecipeMediaRules.mimeTypeForFileName(fileName)
    }

    fun queryMeta(resolver: ContentResolver, uri: Uri): Meta {
        var fileName: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        fileName = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        }
        return Meta(fileName, size)
    }

    fun readBytes(resolver: ContentResolver, uri: Uri): ByteArray? {
        return runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
    }
}
