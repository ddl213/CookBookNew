package com.example.network.bean

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageResponseTest {

    @Test
    fun `hasMore is true when total exceeds loaded pages`() {
        val page = PageResponse(items = listOf(1, 2), page = 1, pageSize = 2, total = 5)
        assertTrue(page.hasMore)
    }

    @Test
    fun `hasMore is false on last page`() {
        val page = PageResponse(items = listOf(5), page = 3, pageSize = 2, total = 5)
        assertFalse(page.hasMore)
    }

    @Test
    fun `empty response has no more pages`() {
        assertFalse(PageResponse<Int>().hasMore)
    }
}
