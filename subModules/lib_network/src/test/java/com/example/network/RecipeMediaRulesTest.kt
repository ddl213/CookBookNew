package com.example.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeMediaRulesTest {

    @Test
    fun `allows jpeg png and webp ignoring case`() {
        assertTrue(RecipeMediaRules.isAllowedMimeType("image/jpeg"))
        assertTrue(RecipeMediaRules.isAllowedMimeType("image/png"))
        assertTrue(RecipeMediaRules.isAllowedMimeType(" IMAGE/WEBP "))
    }

    @Test
    fun `rejects unsupported or blank mime`() {
        assertFalse(RecipeMediaRules.isAllowedMimeType("image/gif"))
        assertFalse(RecipeMediaRules.isAllowedMimeType(null))
        assertFalse(RecipeMediaRules.isAllowedMimeType("  "))
    }

    @Test
    fun `size limit accepts up to five mebibytes`() {
        assertTrue(RecipeMediaRules.isWithinSizeLimit(1))
        assertTrue(RecipeMediaRules.isWithinSizeLimit(RecipeMediaRules.MAX_IMAGE_BYTES))
        assertFalse(RecipeMediaRules.isWithinSizeLimit(0))
        assertFalse(RecipeMediaRules.isWithinSizeLimit(RecipeMediaRules.MAX_IMAGE_BYTES + 1))
    }

    @Test
    fun `mime falls back to file extension`() {
        assertEquals("image/jpeg", RecipeMediaRules.mimeTypeForFileName("a.JPG"))
        assertEquals("image/png", RecipeMediaRules.mimeTypeForFileName("a.png"))
        assertEquals("image/webp", RecipeMediaRules.mimeTypeForFileName("a.webp"))
        assertNull(RecipeMediaRules.mimeTypeForFileName("a.gif"))
        assertNull(RecipeMediaRules.mimeTypeForFileName(null))
    }

    @Test
    fun `upload file name strips directories and falls back by mime`() {
        assertEquals("a.png", RecipeMediaRules.uploadFileName("/tmp/dir/a.png", "image/png"))
        assertEquals("recipe.webp", RecipeMediaRules.uploadFileName(null, "image/webp"))
        assertEquals("recipe.jpg", RecipeMediaRules.uploadFileName("  ", "image/jpeg"))
    }
}
