package com.example.pocketreborn.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {

    @Test
    fun testExtractArticleFromHtmlFixture() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>The Future of Mobile Computing - Tech Journal</title>
                <meta property="og:title" content="The Future of Mobile Computing" />
                <meta name="author" content="Alice Smith" />
                <meta property="og:site_name" content="Tech Journal" />
                <meta property="article:published_time" content="2026-03-01" />
                <meta property="og:image" content="https://techjournal.com/images/hero.jpg" />
                <link rel="canonical" href="https://techjournal.com/future-mobile-computing" />
            </head>
            <body>
                <nav class="nav-bar">
                    <a href="/">Home</a> | <a href="/about">About</a>
                </nav>
                <script>console.log('Ad script');</script>
                <div class="ad-banner">Buy cheap products now!</div>

                <article>
                    <h1>The Future of Mobile Computing</h1>
                    <p class="byline">By Alice Smith</p>

                    <p>Mobile devices are evolving rapidly with enhanced local AI capabilities and on-device processing power.</p>

                    <figure>
                        <img src="/images/device.jpg" alt="A modern mobile device" />
                        <figcaption>Next generation mobile processor</figcaption>
                    </figure>

                    <p>Engineers can now run complex offline models directly on phones without relying on continuous cloud connectivity.</p>

                    <blockquote>Offline-first design ensures accessibility in low bandwidth regions.</blockquote>

                    <p>This paradigm shift enables distraction-free reading apps to save and extract articles reliably.</p>
                </article>

                <div class="comments" id="comments">
                    <h3>User Comments</h3>
                    <p>User1: Great article!</p>
                </div>
                <footer>Copyright 2026 Tech Journal</footer>
            </body>
            </html>
        """.trimIndent()

        val extracted = ArticleExtractor.extract(sampleHtml, "https://techjournal.com/articles/mobile")

        assertEquals("The Future of Mobile Computing", extracted.title)
        assertEquals("Alice Smith", extracted.author)
        assertEquals("Tech Journal", extracted.publisher)
        assertEquals("2026-03-01", extracted.publishedDate)
        assertEquals("https://techjournal.com/future-mobile-computing", extracted.canonicalUrl)
        assertEquals("https://techjournal.com/images/hero.jpg", extracted.featuredImageUrl)

        // Verify clean content excludes nav, scripts, ads, and comments
        assertFalse(extracted.contentHtml.contains("Ad script"))
        assertFalse(extracted.contentHtml.contains("Buy cheap products now!"))
        assertFalse(extracted.contentHtml.contains("User Comments"))

        // Verify image absolute URL resolution
        assertTrue(extracted.images.any { it.url == "https://techjournal.com/images/device.jpg" })

        // Verify plain text contains article content
        assertTrue(extracted.plainText.contains("Mobile devices are evolving rapidly"))
        assertTrue(extracted.plainText.contains("Offline-first design ensures accessibility"))

        assertNotNull(extracted.excerpt)
        assertTrue(extracted.estimatedReadingTimeMinutes >= 1)
    }

    @Test
    fun testEmptyHtmlExtractionHandledGracefully() {
        val emptyHtml = "<html><body></body></html>"
        val extracted = ArticleExtractor.extract(emptyHtml, "https://example.com/empty")

        assertEquals("Untitled Article", extracted.title)
        assertTrue(extracted.plainText.isEmpty())
        assertTrue(extracted.images.isEmpty())
    }
}
