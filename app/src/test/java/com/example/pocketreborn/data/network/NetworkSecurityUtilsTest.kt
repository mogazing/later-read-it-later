package com.example.pocketreborn.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSecurityUtilsTest {

    @Test
    fun testValidPublicUrlsAreSafe() {
        assertTrue(NetworkSecurityUtils.isSafeUrl("https://example.com/article/123"))
        assertTrue(NetworkSecurityUtils.isSafeUrl("http://news.ycombinator.com/item?id=1"))
    }

    @Test
    fun testUnsafeAndLocalUrlsAreBlocked() {
        assertFalse(NetworkSecurityUtils.isSafeUrl("http://localhost:8080"))
        assertFalse(NetworkSecurityUtils.isSafeUrl("http://127.0.0.1/admin"))
        assertFalse(NetworkSecurityUtils.isSafeUrl("file:///sdcard/secret.txt"))
        assertFalse(NetworkSecurityUtils.isSafeUrl("javascript:alert(1)"))
    }

    @Test
    fun testExtractUrlFromSharedText() {
        val sharedText = "Check out this cool article: https://techcrunch.com/2026/01/01/future-tech/! Hope you enjoy it."
        val extracted = NetworkSecurityUtils.extractUrlFromSharedText(sharedText)
        assertEquals("https://techcrunch.com/2026/01/01/future-tech/", extracted)
    }

    @Test
    fun testExtractUrlWithNoUrlReturnsNull() {
        val sharedText = "Just some text without any web link."
        val extracted = NetworkSecurityUtils.extractUrlFromSharedText(sharedText)
        assertNull(extracted)
    }

    @Test
    fun testExtractUrlsFromPublisherHeadlineAndMultipleLinks() {
        val urls = NetworkSecurityUtils.extractUrlsFromSharedText(
            "Check out this article:\nIGN — The best games this year\n" +
                "https://www.ign.com/articles/example-article. " +
                "Related: https://news.example/story"
        )
        assertEquals(
            listOf("https://www.ign.com/articles/example-article", "https://news.example/story"),
            urls
        )
    }

    @Test
    fun testUnsupportedSchemesAreNotExtracted() {
        assertTrue(NetworkSecurityUtils.extractUrlsFromSharedText("javascript:alert(1)").isEmpty())
        assertTrue(NetworkSecurityUtils.extractUrlsFromSharedText("https://").isEmpty())
    }

    @Test
    fun testNormalizeUrlStripsFragment() {
        val rawUrl = "https://example.com/post?id=42#section-2"
        val normalized = NetworkSecurityUtils.normalizeUrl(rawUrl)
        assertEquals("https://example.com/post?id=42", normalized)
    }

    @Test
    fun testNormalizeUrlPreservesEncodedPathAndTrackingQuery() {
        val rawUrl = "https://example.com/a%20b?utm_source=reader&id=42#section"
        assertEquals(
            "https://example.com/a%20b?utm_source=reader&id=42",
            NetworkSecurityUtils.normalizeUrl(rawUrl)
        )
    }
}
