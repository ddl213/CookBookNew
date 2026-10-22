package com.example.network

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NetworkConfigTest {

    private val defaultBaseUrl = "http://10.0.2.2:3000/api/v1/"

    @Before
    fun setUp() {
        NetworkConfig.baseUrl = defaultBaseUrl
    }

    @After
    fun tearDown() {
        // 恢复默认值，避免影响其他测试。
        NetworkConfig.baseUrl = defaultBaseUrl
    }

    @Test
    fun blankValuesReturnNull() {
        assertNull(NetworkConfig.resolveMediaUrl(null))
        assertNull(NetworkConfig.resolveMediaUrl(""))
        assertNull(NetworkConfig.resolveMediaUrl("   "))
    }

    @Test
    fun absoluteUrlIsReturnedUnchanged() {
        assertEquals(
            "https://example.com/a.png",
            NetworkConfig.resolveMediaUrl("https://example.com/a.png")
        )
        assertEquals(
            "http://example.com/a.png",
            NetworkConfig.resolveMediaUrl("http://example.com/a.png")
        )
    }

    @Test
    fun uploadsPathUsesDefaultOrigin() {
        assertEquals(
            "http://10.0.2.2:3000/uploads/a.png",
            NetworkConfig.resolveMediaUrl("/uploads/a.png")
        )
    }

    @Test
    fun uploadsPathUsesCustomOriginWithPortAndPath() {
        NetworkConfig.baseUrl = "https://dev.example.com:8443/cookbook/api/v1/"
        assertEquals(
            "https://dev.example.com:8443/uploads/a.png",
            NetworkConfig.resolveMediaUrl("/uploads/a.png")
        )
    }

    @Test
    fun otherValuesAreReturnedUnchanged() {
        assertEquals(
            "uploads/a.png",
            NetworkConfig.resolveMediaUrl("uploads/a.png")
        )
    }
}
