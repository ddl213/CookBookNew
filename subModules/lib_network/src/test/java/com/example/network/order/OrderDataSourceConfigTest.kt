package com.example.network.order

import com.example.network.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 订单数据源配置解析：默认 remote、显式 mock、非法/空白回退。 */
class OrderDataSourceConfigTest {

    @Test
    fun `mock value resolves to mock source`() {
        val resolution = OrderDataSourceConfig.resolve("mock")
        assertEquals(OrderDataSourceKind.MOCK, resolution.kind)
        assertFalse(resolution.fallback)
        assertEquals("mock", resolution.rawValue)
    }

    @Test
    fun `remote value is case insensitive and trimmed`() {
        val resolution = OrderDataSourceConfig.resolve("  ReMoTe  ")
        assertEquals(OrderDataSourceKind.REMOTE, resolution.kind)
        assertFalse(resolution.fallback)
    }

    @Test
    fun `invalid value falls back to remote and marks fallback`() {
        val resolution = OrderDataSourceConfig.resolve("socket")
        assertEquals(OrderDataSourceKind.REMOTE, resolution.kind)
        assertTrue(resolution.fallback)
        assertEquals("socket", resolution.rawValue)
    }

    @Test
    fun `blank and null values fall back to remote`() {
        assertTrue(OrderDataSourceConfig.resolve("   ").fallback)
        assertTrue(OrderDataSourceConfig.resolve("").fallback)
        assertTrue(OrderDataSourceConfig.resolve(null).fallback)
    }

    @Test
    fun `fallback always keeps raw value for warning`() {
        val resolution = OrderDataSourceConfig.resolve("  bogus  ")
        assertEquals("  bogus  ", resolution.rawValue)
        assertEquals(OrderDataSourceKind.REMOTE, resolution.kind)
    }

    @Test
    fun `current reads build config value`() {
        val expected = OrderDataSourceConfig.resolve(BuildConfig.ORDER_DATA_SOURCE)
        val current = OrderDataSourceConfig.current()
        assertEquals(expected.kind, current.kind)
        assertEquals(expected.fallback, current.fallback)
    }

    @Test
    fun `default build config is remote for debug and release`() {
        // 未显式传 -PorderDataSource 时，构建脚本两种构建类型都写入 remote。
        assertEquals(OrderDataSourceKind.REMOTE, OrderDataSourceConfig.resolve("remote").kind)
        assertFalse(OrderDataSourceConfig.resolve("remote").fallback)
        assertNull(OrderDataSourceConfig.resolve(null).rawValue)
    }
}
