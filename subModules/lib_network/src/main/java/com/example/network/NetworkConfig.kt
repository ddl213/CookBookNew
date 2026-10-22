package com.example.network

/**
 * 后端地址配置。
 *
 * 默认值用于 Android 模拟器访问宿主机。
 * 真机调试时请改为开发机局域网 IP（例如 http://192.168.1.10:3000/api/v1/），
 * 并让 CookBookService 以 HOST=0.0.0.0 启动，确保手机可以访问。
 *
 * 该值需要在首次发起网络请求前设置；ApiClient 首次使用时创建并缓存 Retrofit 实例。
 */
object NetworkConfig {
    var baseUrl: String = "http://10.0.2.2:3000/api/v1/"

    /**
     * 将后端返回的媒体地址解析为可直接加载的 URL。
     *
     * - null / 空白 → null
     * - http:// 或 https:// 开头 → 原样返回
     * - / 开头（如 /uploads/xxx.png）→ 拼接后端 origin（scheme + host + port）
     * - 其他值 → 原样返回，保持兼容且不抛异常
     */
    fun resolveMediaUrl(value: String?): String? {
        val raw = value?.trim()
        if (raw.isNullOrEmpty()) return null
        if (raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        ) {
            return raw
        }
        if (!raw.startsWith("/")) return raw
        val origin = originOf(baseUrl) ?: return raw
        return origin + raw
    }

    /**
     * 从 baseUrl 中提取 scheme + host + port，忽略 /api/v1/ 等路径部分。
     */
    private fun originOf(url: String): String? {
        return runCatching {
            val uri = java.net.URI(url)
            val scheme = uri.scheme ?: return null
            val host = uri.host ?: return null
            val port = if (uri.port > 0) ":${uri.port}" else ""
            "$scheme://$host$port"
        }.getOrNull()
    }
}
