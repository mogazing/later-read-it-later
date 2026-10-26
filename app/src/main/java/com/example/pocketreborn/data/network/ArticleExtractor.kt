package com.example.pocketreborn.data.network

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist
import java.net.URI

data class ExtractedImage(
    val url: String,
    val caption: String? = null
)

data class ExtractedArticle(
    val title: String,
    val subtitle: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val canonicalUrl: String? = null,
    val featuredImageUrl: String? = null,
    val contentHtml: String,
    val plainText: String,
    val excerpt: String,
    val images: List<ExtractedImage>,
    val estimatedReadingTimeMinutes: Int
)

object ArticleExtractor {

    fun extract(html: String, pageUrl: String): ExtractedArticle {
        val doc: Document = Jsoup.parse(html, pageUrl)

        // 1. Metadata extraction
        val canonicalUrl = doc.select("link[rel=canonical]").attr("abs:href").ifBlank { null }
        val title = extractTitle(doc)
        val subtitle = extractSubtitle(doc)
        val author = extractAuthor(doc)
        val publisher = extractPublisher(doc, pageUrl)
        val publishedDate = extractPublishedDate(doc)
        val featuredImageUrl = doc.select("meta[property=og:image]").attr("abs:content")
            .ifBlank { doc.select("meta[name=twitter:image]").attr("abs:content") }
            .ifBlank { null }

        // 2. Clean Document for Article Body Extraction
        val cleanDoc = doc.clone()

        // Remove website clutter, ads, navigation, promotional widgets, and share tools
        cleanDoc.select(
            "script, style, noscript, nav, iframe, form, header, footer, aside, " +
            ".ad, .ads, .advertisement, .ad-unit, .dfp-ad, .sponsor-content, .outbrain, .taboola, #dfp-ad, [data-ad], " +
            "#comments, .comments, .comments-section, .disqus, " +
            ".social-share, .share-bar, .sharing-tools, .gift-link, .gift-button, .c-entry-hero__share, .c-social-buttons, " +
            "[aria-label*=share], [aria-label*=gift], " +
            ".cookie-banner, .popup, .paywall-prompt, .subscribe-prompt, .newsletter-signup, .c-newsletter-signup, .membership-prompt, " +
            ".recirc-list, .related-stories, .related-articles, .recommended-stories, .most-popular, [data-analytics-type=recirc], .m-recirc, .c-compact-story, .read-next, .more-from-site, .c-recirc-list, " +
            "[role=banner], [role=navigation], [role=contentinfo]"
        ).remove()

        // Find best article body container element
        val bodyCandidate = findBestContentElement(cleanDoc)

        // Remove duplicate Title/H1 at the start of the body to prevent repeating the title
        val firstH1 = bodyCandidate.select("h1, h2").firstOrNull()
        if (firstH1 != null) {
            val h1Text = firstH1.text().trim()
            if (h1Text.isNotBlank() && (h1Text.equals(title, ignoreCase = true) || title.contains(h1Text, ignoreCase = true))) {
                firstH1.remove()
            }
        }

        // Make all image src and link href absolute
        bodyCandidate.select("img").forEach { img ->
            val absSrc = img.attr("abs:src").ifBlank { img.attr("abs:data-src") }.ifBlank { img.attr("abs:data-lazy-src") }
            if (absSrc.isNotBlank()) {
                img.attr("src", absSrc)
            }
        }
        bodyCandidate.select("a").forEach { a ->
            val absHref = a.attr("abs:href")
            if (absHref.isNotBlank()) {
                a.attr("href", absHref)
            }
        }

        // Collect inline images with captions
        val images = mutableListOf<ExtractedImage>()
        bodyCandidate.select("img").forEach { img ->
            val src = img.attr("src")
            if (src.isNotBlank() && NetworkSecurityUtils.isSafeUrl(src)) {
                val caption = img.attr("alt").ifBlank {
                    img.parent()?.select("figcaption")?.text()?.ifBlank { null }
                }
                images.add(ExtractedImage(url = src, caption = caption))
            }
        }

        // Clean HTML allowing rich, semantic article structure
        val safelist = Safelist.relaxed()
            .addTags("article", "section", "header", "footer", "figcaption", "figure", "hr", "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "mark")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addAttributes("a", "href", "title")

        val cleanedHtml = Jsoup.clean(bodyCandidate.html(), pageUrl, safelist)

        // Generate clean plain text for Reader, TTS, and FTS with explicit paragraph/block boundaries
        val plainTextDoc = Jsoup.parse(cleanedHtml)
        plainTextDoc.select("p, h1, h2, h3, h4, h5, h6, li, blockquote, figcaption, figure, section").forEach { el ->
            el.prepend("\n\n").append("\n\n")
        }

        val rawPlainText = plainTextDoc.text()
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        val excerpt = if (subtitle != null && subtitle.isNotBlank()) {
            subtitle
        } else if (rawPlainText.length > 200) {
            rawPlainText.substring(0, 200).replace("\n", " ").trim() + "..."
        } else {
            rawPlainText
        }

        val wordCount = rawPlainText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val readingTime = maxOf(1, (wordCount / 200.0).toInt())

        return ExtractedArticle(
            title = title,
            subtitle = subtitle,
            author = author,
            publisher = publisher,
            publishedDate = publishedDate,
            canonicalUrl = canonicalUrl,
            featuredImageUrl = featuredImageUrl,
            contentHtml = cleanedHtml,
            plainText = rawPlainText,
            excerpt = excerpt,
            images = images.distinctBy { it.url },
            estimatedReadingTimeMinutes = readingTime
        )
    }

    private fun extractTitle(doc: Document): String {
        val ogTitle = doc.select("meta[property=og:title]").attr("content")
        if (ogTitle.isNotBlank()) return ogTitle.trim()

        val twitterTitle = doc.select("meta[name=twitter:title]").attr("content")
        if (twitterTitle.isNotBlank()) return twitterTitle.trim()

        val h1 = doc.select("h1").first()?.text()
        if (!h1.isNullOrBlank()) return h1.trim()

        val title = doc.title()
        if (title.isNotBlank()) return title.trim()

        return "Untitled Article"
    }

    private fun extractSubtitle(doc: Document): String? {
        val ogDesc = doc.select("meta[property=og:description]").attr("content")
            .ifBlank { doc.select("meta[name=description]").attr("content") }
            .ifBlank { doc.select("meta[name=twitter:description]").attr("content") }
        if (ogDesc.isNotBlank()) return ogDesc.trim()

        val subEl = doc.select(".subtitle, .article-subtitle, .entry-subtitle, .dek, .c-entry-summary").first()
        val text = subEl?.text()
        return if (!text.isNullOrBlank()) text.trim() else null
    }

    private fun extractAuthor(doc: Document): String? {
        val metaAuthor = doc.select("meta[name=author]").attr("content")
            .ifBlank { doc.select("meta[property=article:author]").attr("content") }
        if (metaAuthor.isNotBlank()) return metaAuthor.trim()

        val authorEl = doc.select("[rel=author], .byline, .author, .byline-author, .c-byline__author-name").first()
        val text = authorEl?.text()
        return if (!text.isNullOrBlank()) text.trim() else null
    }

    private fun extractPublisher(doc: Document, pageUrl: String): String? {
        val ogSiteName = doc.select("meta[property=og:site_name]").attr("content")
        if (ogSiteName.isNotBlank()) return ogSiteName.trim()

        return try {
            val host = URI(pageUrl).host
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            null
        }
    }

    private fun extractPublishedDate(doc: Document): String? {
        val date = doc.select("meta[property=article:published_time]").attr("content")
            .ifBlank { doc.select("meta[name=pubdate]").attr("content") }
            .ifBlank { doc.select("time").attr("datetime") }
            .ifBlank { doc.select("time").text() }
        return if (date.isNotBlank()) date.trim() else null
    }

    private fun findBestContentElement(doc: Document): Element {
        // Priority 1: <article> tag with meaningful content
        val articleTag = doc.select("article").firstOrNull { it.text().length > 100 }
        if (articleTag != null) return articleTag

        // Priority 2: <main> or [role=main]
        val mainTag = doc.select("main, [role=main]").firstOrNull { it.text().length > 100 }
        if (mainTag != null) return mainTag

        // Priority 3: common article body selectors
        val classCandidate = doc.select(".post-content, .article-body, .entry-content, .article-text, #article-body, #content, .c-entry-content")
            .firstOrNull { it.text().length > 100 }
        if (classCandidate != null) return classCandidate

        // Priority 4: Score top candidates by paragraph density
        val candidates = doc.select("div, section")
        var bestElement: Element = doc.body() ?: doc
        var maxScore = 0

        for (el in candidates) {
            val pCount = el.select("p").size
            val textLength = el.text().length
            if (textLength < 100) continue

            val score = pCount * 100 + textLength / 10
            if (score > maxScore) {
                maxScore = score
                bestElement = el
            }
        }

        return bestElement
    }
}
