package com.example.network.bean

/**
 * 图片上传响应：{ url: "/uploads/<uuid>.<ext>" }。
 * 展示前必须经 NetworkConfig.resolveMediaUrl 解析。
 */
data class UploadResponse(
    val url: String = ""
)
