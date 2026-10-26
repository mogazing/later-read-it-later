package com.example.pocketreborn.data.discovery

import android.content.Context
import android.net.Uri
import android.util.Xml
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private val Context.discoveryStore by preferencesDataStore(name = "discovery_cache")

data class DiscoveryArticle(
    val title: String,
    val url: String,
    val publisher: String,
    val description: String,
    val publishedAt: String?,
    val imageUrl: String?,
    val category: String,
    val accessibility: String = "unknown"
)

data class DiscoverySource(
    val name: String,
    val category: String,
    val feedUrl: String,
    val enabled: Boolean = true
)

data class CachedDiscoveryBatch(
    val articles: List<DiscoveryArticle>,
    val refreshedAt: Long
)

class DiscoveryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val topics = listOf(
        "All Topics",
        "Technology",
        "PC Gaming",
        "Science",
        "Psychology",
        "Self-Improvement",
        "Philosophy",
        "Investigative Journalism",
        "Long-Form Essays",
        "True Crime",
        "DIY & Practical Skills",
        "History & Culture",
        "Weird & Fascinating",
        "Satire & Humor"
    )

    private val allSources = listOf(
        // Technology
        DiscoverySource("Vox", "Technology", "https://www.vox.com/rss/index.xml"),
        DiscoverySource("WIRED", "Technology", "https://www.wired.com/feed/rss"),
        DiscoverySource("Ars Technica", "Technology", "https://feeds.arstechnica.com/arstechnica/index"),
        DiscoverySource("The Verge", "Technology", "https://www.theverge.com/rss/index.xml"),

        // PC Gaming
        DiscoverySource("PC Gamer", "PC Gaming", "https://www.pcgamer.com/rss/"),
        DiscoverySource("Rock Paper Shotgun", "PC Gaming", "https://www.rockpapershotgun.com/feed"),
        DiscoverySource("Eurogamer", "PC Gaming", "https://www.eurogamer.net/feed"),
        DiscoverySource("Polygon", "PC Gaming", "https://www.polygon.com/rss/index.xml"),

        // Science
        DiscoverySource("Quanta Magazine", "Science", "https://api.quantamagazine.org/feed/"),
        DiscoverySource("Scientific American", "Science", "https://www.scientificamerican.com/feed/"),
        DiscoverySource("Phys.org", "Science", "https://phys.org/rss-feed/"),

        // Psychology
        DiscoverySource("Psychology Today", "Psychology", "https://www.psychologytoday.com/us/front/feed"),
        DiscoverySource("PsyBlog", "Psychology", "https://www.spring.org.uk/feed"),

        // Self-Improvement
        DiscoverySource("Zen Habits", "Self-Improvement", "https://zenhabits.net/feed/"),
        DiscoverySource("Farnam Street", "Self-Improvement", "https://fs.blog/feed/"),

        // Philosophy
        DiscoverySource("Daily Nous", "Philosophy", "https://dailynous.com/feed/"),
        DiscoverySource("Aeon Essays", "Philosophy", "https://aeon.co/feed.rss"),

        // Investigative Journalism
        DiscoverySource("ProPublica", "Investigative Journalism", "https://www.propublica.org/feeds/propublica/main"),
        DiscoverySource("The Marshall Project", "Investigative Journalism", "https://www.themarshallproject.org/rss/recent.xml"),

        // Long-Form Essays
        DiscoverySource("Longform", "Long-Form Essays", "https://longform.org/feed"),
        DiscoverySource("The Atavist Magazine", "Long-Form Essays", "https://magazine.atavist.com/feed/"),

        // True Crime
        DiscoverySource("CrimeReads", "True Crime", "https://crimereads.com/feed/"),

        // DIY & Practical Skills
        DiscoverySource("MakeUseOf", "DIY & Practical Skills", "https://www.makeuseof.com/feed/"),
        DiscoverySource("Hackaday", "DIY & Practical Skills", "https://hackaday.com/blog/feed/"),

        // History & Culture
        DiscoverySource("Smithsonian Magazine", "History & Culture", "https://www.smithsonianmag.com/rss/articles/"),
        DiscoverySource("Open Culture", "History & Culture", "https://www.openculture.com/feed"),

        // Weird & Fascinating
        DiscoverySource("Atlas Obscura", "Weird & Fascinating", "https://www.atlasobscura.com/feeds/latest"),
        DiscoverySource("Damn Interesting", "Weird & Fascinating", "https://www.damninteresting.com/feed/"),

        // Satire & Humor
        DiscoverySource("The Onion", "Satire & Humor", "https://www.theonion.com/rss"),
        DiscoverySource("Hard Drive", "Satire & Humor", "https://hard-drive.net/feed/")
    )

    suspend fun readCachedTopic(topic: String): CachedDiscoveryBatch = withContext(Dispatchers.IO) {
        val prefs = appContext.discoveryStore.data.first()
        val key = stringPreferencesKey("articles_${sanitizeKey(topic)}")
        val timeKey = longPreferencesKey("refreshed_at_${sanitizeKey(topic)}")
        val raw = prefs[key] ?: return@withContext CachedDiscoveryBatch(emptyList(), 0L)

        val articles = runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { index ->
                val item = json.optJSONObject(index) ?: return@mapNotNull null
                DiscoveryArticle(
                    title = item.optString("title"),
                    url = item.optString("url"),
                    publisher = item.optString("publisher"),
                    description = item.optString("description"),
                    publishedAt = item.optString("publishedAt").takeIf(String::isNotBlank),
                    imageUrl = item.optString("imageUrl").takeIf(String::isNotBlank),
                    category = item.optString("category"),
                    accessibility = item.optString("accessibility", "unknown")
                ).takeIf { it.title.isNotBlank() && it.url.isNotBlank() }
            }
        }.getOrElse { emptyList() }

        CachedDiscoveryBatch(articles, prefs[timeKey] ?: 0L)
    }

    suspend fun fetchTopicBatch(topic: String, previousUrls: Set<String>): List<DiscoveryArticle> = coroutineScope {
        val selectedSources = if (topic == "All Topics") {
            allSources
        } else {
            allSources.filter { it.category.equals(topic, ignoreCase = true) }.ifEmpty { allSources }
        }

        val responses = selectedSources.map { source ->
            async(Dispatchers.IO) {
                try {
                    fetchSource(source)
                } catch (error: Exception) {
                    android.util.Log.w("Discovery", "${source.name} feed failed: ${error.message}")
                    emptyList()
                }
            }
        }.awaitAll()

        val candidates = responses.flatten()
            .distinctBy { canonicalKey(it.url) }
            .filter { !isClearlyLowQuality(it) }
            .map { it to score(it) }
            .filter { it.second >= 0 }
            .sortedByDescending { it.second }

        if (candidates.isEmpty()) {
            throw IllegalStateException("No feeds returned usable articles for $topic.")
        }

        val unseen = candidates.filter { canonicalKey(it.first.url) !in previousUrls }
        val selected = diverseSelection(if (unseen.size >= 10) unseen else candidates)
        selected.take(20)
    }

    suspend fun saveTopicBatch(topic: String, articles: List<DiscoveryArticle>, refreshedAt: Long) {
        val json = JSONArray()
        articles.forEach { article ->
            json.put(
                JSONObject()
                    .put("title", article.title)
                    .put("url", article.url)
                    .put("publisher", article.publisher)
                    .put("description", article.description)
                    .put("publishedAt", article.publishedAt ?: "")
                    .put("imageUrl", article.imageUrl ?: "")
                    .put("category", article.category)
                    .put("accessibility", article.accessibility)
            )
        }
        val key = stringPreferencesKey("articles_${sanitizeKey(topic)}")
        val timeKey = longPreferencesKey("refreshed_at_${sanitizeKey(topic)}")

        appContext.discoveryStore.edit { prefs ->
            prefs[key] = json.toString()
            prefs[timeKey] = refreshedAt
        }
    }

    private fun fetchSource(source: DiscoverySource): List<DiscoveryArticle> {
        val request = Request.Builder()
            .url(source.feedUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept", "application/atom+xml, application/rss+xml, application/xml, text/xml")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("${source.name} feed returned HTTP ${response.code}.")
            val body = response.body?.string() ?: throw IllegalStateException("${source.name} feed was empty.")
            return parseFeed(body, source)
        }
    }

    private fun parseFeed(xml: String, source: DiscoverySource): List<DiscoveryArticle> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(StringReader(xml))
        }
        val articles = mutableListOf<DiscoveryArticle>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name.lowercase(Locale.ROOT) in setOf("item", "entry")) {
                parseEntry(parser, source)?.let(articles::add)
            }
            event = parser.next()
        }
        return articles
    }

    private fun parseEntry(parser: XmlPullParser, source: DiscoverySource): DiscoveryArticle? {
        var title = ""
        var url = ""
        var description = ""
        var publishedAt: String? = null
        var imageUrl: String? = null
        val entryName = parser.name
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    val name = parser.name.lowercase(Locale.ROOT)
                    when (name) {
                        "link" -> {
                            val href = parser.getAttributeValue(null, "href")
                            if (!href.isNullOrBlank() && (parser.getAttributeValue(null, "rel") == null ||
                                    parser.getAttributeValue(null, "rel") == "alternate")
                            ) {
                                url = href
                            } else if (href.isNullOrBlank()) {
                                val text = readElementText(parser)
                                if (url.isBlank()) url = text
                                depth--
                            }
                        }
                        "content", "thumbnail" -> {
                            imageUrl = imageUrl ?: parser.getAttributeValue(null, "url")
                        }
                        "enclosure" -> {
                            val type = parser.getAttributeValue(null, "type").orEmpty()
                            if (type.startsWith("image/")) imageUrl = imageUrl ?: parser.getAttributeValue(null, "url")
                        }
                        "title" -> if (title.isBlank()) {
                            title = readElementText(parser).stripMarkup()
                            depth--
                        }
                        "description", "summary", "encoded", "content:encoded" -> {
                            val text = readElementText(parser).stripMarkup()
                            depth--
                            if (text.length > description.length) {
                                description = text
                                if (imageUrl == null) imageUrl = imageFromMarkup(text)
                            }
                        }
                        "pubdate", "published", "updated", "date" -> {
                            if (publishedAt == null) {
                                publishedAt = readElementText(parser).trim()
                                depth--
                            }
                        }
                    }
                    if (name == "link" && url.isBlank()) {
                        val text = parser.getAttributeValue(null, "href")
                        if (!text.isNullOrBlank()) url = text
                    }
                }
                XmlPullParser.END_TAG -> depth--
            }
        }
        if (entryName.isBlank()) return null
        val normalizedUrl = normalizeArticleUrl(url)
        if (title.isBlank() || normalizedUrl == null) return null
        return DiscoveryArticle(
            title = title.trim(),
            url = normalizedUrl,
            publisher = source.name,
            description = description.trim().take(500),
            publishedAt = publishedAt,
            imageUrl = imageUrl?.let(::normalizeArticleUrl),
            category = source.category
        )
    }

    private fun score(article: DiscoveryArticle): Int {
        val text = "${article.title} ${article.description}".lowercase(Locale.ROOT)
        var score = 0
        if (article.description.length >= 100) score += 3
        val lowValue = listOf("deals", "coupon", "discount", "preorder", "patch notes")
        if (lowValue.any(text::contains)) score -= 10
        return score
    }

    private fun diverseSelection(scored: List<Pair<DiscoveryArticle, Int>>): List<DiscoveryArticle> {
        val pending = scored.toMutableList()
        val selected = mutableListOf<DiscoveryArticle>()
        val sourceCounts = mutableMapOf<String, Int>()
        while (pending.isNotEmpty() && selected.size < 20) {
            val candidateIndex = pending.indices.maxByOrNull { index ->
                val article = pending[index].first
                pending[index].second - (sourceCounts[article.publisher].orZero() * 4)
            } ?: break
            val article = pending.removeAt(candidateIndex).first
            selected += article
            sourceCounts[article.publisher] = sourceCounts[article.publisher].orZero() + 1
        }
        return selected
    }

    private fun isClearlyLowQuality(article: DiscoveryArticle): Boolean {
        val text = "${article.title} ${article.description}".lowercase(Locale.ROOT)
        return listOf("sponsored", "affiliate", "promo code", "buy now").any(text::contains)
    }

    private fun canonicalKey(url: String): String = runCatching {
        val uri = URI(url)
        val query = uri.rawQuery.orEmpty().split("&")
            .filterNot { it.substringBefore("=").lowercase(Locale.ROOT).startsWith("utm_") }
            .filterNot { it.substringBefore("=").lowercase(Locale.ROOT) in setOf("ref", "source", "fbclid", "gclid") }
            .joinToString("&")
            .takeIf(String::isNotBlank)
        URI(uri.scheme.lowercase(Locale.ROOT), uri.authority.lowercase(Locale.ROOT), uri.path.trimEnd('/'), query, null)
            .toString()
    }.getOrDefault(url.substringBefore("#").trimEnd('/'))

    private fun normalizeArticleUrl(url: String): String? = runCatching {
        val uri = Uri.parse(url.trim())
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) null
        else uri.buildUpon().fragment(null).build().toString()
    }.getOrNull()

    private fun imageFromMarkup(markup: String): String? =
        Regex("""<img[^>]+src=["']([^"']+)""", RegexOption.IGNORE_CASE)
            .find(markup)?.groupValues?.getOrNull(1)

    private fun readElementText(parser: XmlPullParser): String =
        runCatching { parser.nextText() }.getOrDefault("")

    private fun String.stripMarkup(): String = replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun sanitizeKey(topic: String): String = topic.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "_")

    private fun Int?.orZero(): Int = this ?: 0
}
