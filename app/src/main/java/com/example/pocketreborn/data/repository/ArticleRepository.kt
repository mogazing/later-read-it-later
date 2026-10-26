package com.example.pocketreborn.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import com.example.pocketreborn.data.local.dao.ArticleDao
import com.example.pocketreborn.data.local.entity.ArticleEntity
import com.example.pocketreborn.data.local.entity.ArticleImageEntity
import com.example.pocketreborn.data.network.ArticleExtractor
import com.example.pocketreborn.data.network.ImageDownloader
import com.example.pocketreborn.data.network.NetworkSecurityUtils
import com.example.pocketreborn.data.network.UrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.net.ssl.SSLException

sealed class SaveResult {
    data class Queued(val articleId: String) : SaveResult()
    data class Success(val articleId: String, val isPartial: Boolean) : SaveResult()
    data class ExistingDuplicate(val articleId: String) : SaveResult()
    data class Error(
        val message: String,
        val articleId: String? = null,
        val isRetryable: Boolean = false
    ) : SaveResult()
}

class ArticleRepository(
    private val articleDao: ArticleDao,
    private val urlResolver: UrlResolver = UrlResolver(),
    private val context: Context
) {
    private val imageDownloader = ImageDownloader(context)

    fun getAllArticles(): Flow<List<ArticleEntity>> = articleDao.getAllActiveArticles()

    fun getAllActiveArticles(): Flow<List<ArticleEntity>> = articleDao.getAllActiveArticles()

    fun getArchivedArticles(): Flow<List<ArticleEntity>> = articleDao.getArchivedArticles()

    fun getFavoriteArticles(): Flow<List<ArticleEntity>> = articleDao.getFavoriteArticles()

    fun getArticlesByFolder(folderId: String): Flow<List<ArticleEntity>> = articleDao.getArticlesByFolder(folderId)

    fun getArticleByIdFlow(id: String): Flow<ArticleEntity?> = articleDao.getArticleByIdFlow(id)

    suspend fun getArticleById(id: String): ArticleEntity? = articleDao.getArticleById(id)

    suspend fun getPendingDownloadIds(): List<String> = articleDao.getPendingDownloadIds()

    fun searchArticles(query: String): Flow<List<ArticleEntity>> = articleDao.searchArticles(query)

    suspend fun markAsOpened(id: String) {
        articleDao.markAsOpened(id)
    }

    suspend fun insertTestArticle(): SaveResult = withContext(Dispatchers.IO) {
        val testUrl = "later://test-article-tts"
        val existing = articleDao.getArticleByUrl(testUrl)
        if (existing != null) {
            return@withContext SaveResult.ExistingDuplicate(existing.id)
        }

        val testContent = """
            Reading long articles offline with speech synthesis provides a calm, distraction-free experience wherever you go. Modern mobile operating systems feature built-in neural text-to-speech engines capable of generating natural human voices directly on-device without an active internet connection.

            When you save an article to Later, the application extracts the essential text and structural paragraphs while stripping away ads, popups, and unnecessary scripts. This clean text format makes it easy to read on screen and ensures seamless conversion into spoken audio.

            Text-to-speech processing works by dividing long articles into manageable sentence and paragraph chunks. Each chunk is submitted sequentially to Android's TextToSpeech engine. This chunking strategy prevents buffer overflows, allows precise pause and resume controls, and tracks exact sentence progress as speech progresses.

            Audio focus management ensures that listening seamlessly respects incoming phone calls, media notifications, and other audio playback on your phone. When audio focus is temporarily lost, speech pauses gracefully and automatically resumes once focus returns.

            With offline speech synthesis, you can listen to your saved reading list while commuting, exercising, or traveling in areas with unreliable cellular network coverage. Try testing playback controls like speed adjustment, skipping forward to the next paragraph, and rewinding to review previous sections!
        """.trimIndent()

        val article = ArticleEntity(
            id = "test_article_tts_sample",
            originalUrl = testUrl,
            canonicalUrl = testUrl,
            title = "TTS Test Article: Offline Speech & Distraction-Free Reading",
            author = "Later Engineering",
            publisher = "Later Test Suite",
            publishedDate = "2026-03-01",
            content = testContent.replace("\n\n", "<p>"),
            plainText = testContent,
            excerpt = "A sample article pre-loaded locally to test offline text-to-speech, reading progress, and playback controls.",
            downloadState = "SAVED_OFFLINE",
            estimatedReadingTimeMinutes = 2,
            dateSaved = System.currentTimeMillis()
        )

        try {
            articleDao.insertArticle(article)
        } catch (e: SQLiteConstraintException) {
            val duplicate = articleDao.getArticleByUrl(testUrl)
            if (duplicate != null) return@withContext SaveResult.ExistingDuplicate(duplicate.id)
            throw e
        }
        SaveResult.Success(article.id, isPartial = false)
    }

    suspend fun queueArticleUrl(rawUrl: String): SaveResult = withContext(Dispatchers.IO) {
        val candidates = NetworkSecurityUtils.extractUrlsFromSharedText(rawUrl)
        val url = when {
            candidates.size == 1 -> candidates.single()
            candidates.size > 1 -> return@withContext SaveResult.Error("More than one link was found. Enter or share one article link at a time.")
            NetworkSecurityUtils.isValidWebUrl(rawUrl.trim()) -> NetworkSecurityUtils.normalizeUrl(rawUrl.trim())
            else -> return@withContext SaveResult.Error("Enter a valid HTTP or HTTPS article URL.")
        }
        val normalizedUrl = NetworkSecurityUtils.normalizeUrl(url)
        val existing = articleDao.getArticleByUrl(normalizedUrl)
        if (existing != null) return@withContext SaveResult.ExistingDuplicate(existing.id)

        val article = ArticleEntity(
            id = UUID.randomUUID().toString(),
            originalUrl = normalizedUrl,
            title = normalizedUrl,
            content = "",
            plainText = "",
            downloadState = "QUEUED"
        )
        try {
            articleDao.insertArticle(article)
        } catch (e: SQLiteConstraintException) {
            val duplicate = articleDao.getArticleByUrl(normalizedUrl)
            if (duplicate != null) return@withContext SaveResult.ExistingDuplicate(duplicate.id)
            throw e
        }
        SaveResult.Queued(article.id)
    }

    suspend fun queueDiscoveryArticle(
        url: String,
        title: String,
        publisher: String,
        excerpt: String?,
        publishedDate: String?,
        imageUrl: String?
    ): SaveResult = withContext(Dispatchers.IO) {
        val normalizedUrl = NetworkSecurityUtils.normalizeUrl(url)
        if (!NetworkSecurityUtils.isValidWebUrl(normalizedUrl)) {
            return@withContext SaveResult.Error("This recommendation does not have a valid article URL.")
        }
        val existing = articleDao.getArticleByUrl(normalizedUrl)
        if (existing != null) return@withContext SaveResult.ExistingDuplicate(existing.id)
        val article = ArticleEntity(
            id = UUID.randomUUID().toString(),
            originalUrl = normalizedUrl,
            canonicalUrl = normalizedUrl,
            title = title.ifBlank { normalizedUrl },
            publisher = publisher.ifBlank { null },
            publishedDate = publishedDate,
            content = "",
            plainText = "",
            excerpt = excerpt?.takeIf(String::isNotBlank),
            featuredImageUrl = imageUrl,
            downloadState = "QUEUED"
        )
        try {
            articleDao.insertArticle(article)
        } catch (e: SQLiteConstraintException) {
            val duplicate = articleDao.getArticleByUrl(normalizedUrl)
            if (duplicate != null) return@withContext SaveResult.ExistingDuplicate(duplicate.id)
            throw e
        }
        SaveResult.Queued(article.id)
    }

    suspend fun processQueuedArticle(articleId: String): SaveResult = withContext(Dispatchers.IO) {
        val queued = articleDao.getArticleById(articleId)
            ?: return@withContext SaveResult.Error("The queued link no longer exists.")
        if (queued.downloadState == "SAVED_OFFLINE" || queued.downloadState == "PARTIALLY_SAVED") {
            return@withContext SaveResult.Success(articleId, queued.downloadState == "PARTIALLY_SAVED")
        }
        if (!NetworkSecurityUtils.isValidWebUrl(queued.originalUrl)) {
            articleDao.updateDownloadState(articleId, "FAILED", "This link uses an unsupported or restricted network address.")
            return@withContext SaveResult.Error("This link uses an unsupported or restricted network address.", articleId)
        }

        articleDao.updateDownloadState(articleId, "RESOLVING")
        val pageResult = urlResolver.resolveAndFetch(queued.originalUrl)
        if (pageResult.isFailure) {
            val failure = pageResult.exceptionOrNull()
            val message = failure?.message ?: "Failed to retrieve the web page."
            val retryable = failure !is IllegalArgumentException &&
                failure !is SecurityException &&
                failure !is SSLException &&
                (!message.startsWith("HTTP Error 4") || message.contains("429"))

            val fallbackState = if (!retryable && queued.title.isNotBlank() && queued.title != queued.originalUrl) {
                "BOOKMARK_ONLY"
            } else if (retryable) {
                "RETRY_PENDING"
            } else {
                "FAILED"
            }

            articleDao.updateDownloadState(articleId, fallbackState, message)
            return@withContext SaveResult.Error(message, articleId, retryable)
        }

        val resolved = pageResult.getOrThrow()
        articleDao.updateDownloadState(articleId, "EXTRACTING")
        val extracted = try {
            ArticleExtractor.extract(resolved.htmlContent, resolved.finalUrl)
        } catch (e: Exception) {
            articleDao.updateDownloadState(articleId, "BOOKMARK_ONLY", "Article extraction failed: ${e.message}")
            return@withContext SaveResult.Error("Article extraction failed: ${e.message}", articleId)
        }
        if (extracted.plainText.isBlank()) {
            articleDao.updateDownloadState(articleId, "BOOKMARK_ONLY", "No readable article text was found on this page.")
            return@withContext SaveResult.Error("No readable article text was found on this page.", articleId)
        }

        val featuredImageUrl = extracted.featuredImageUrl
            ?.takeIf(String::isNotBlank)
            ?: queued.featuredImageUrl?.takeIf(String::isNotBlank)
        val extractedArticle = queued.copy(
            canonicalUrl = extracted.canonicalUrl,
            title = extracted.title,
            author = extracted.author,
            publisher = extracted.publisher,
            publishedDate = extracted.publishedDate,
            content = extracted.contentHtml,
            plainText = extracted.plainText,
            excerpt = extracted.excerpt,
            featuredImageUrl = featuredImageUrl,
            estimatedReadingTimeMinutes = extracted.estimatedReadingTimeMinutes,
            downloadState = "DOWNLOADING_IMAGES",
            downloadError = null,
            lastUpdated = System.currentTimeMillis()
        )
        articleDao.updateArticle(extractedArticle)

        var featuredLocalPath: String? = null
        var imageFailures = 0
        if (!extracted.featuredImageUrl.isNullOrBlank()) {
            val result = imageDownloader.downloadImage(articleId, extracted.featuredImageUrl)
            featuredLocalPath = result.getOrNull()
            if (featuredLocalPath == null) imageFailures++
        }

        val imageEntities = extracted.images.take(15).map { image ->
            val path = imageDownloader.downloadImage(articleId, image.url).getOrNull()
            if (path == null) imageFailures++
            ArticleImageEntity(
                id = UUID.randomUUID().toString(),
                articleId = articleId,
                remoteUrl = image.url,
                localPath = path,
                caption = image.caption,
                isDownloaded = path != null
            )
        }
        if (imageEntities.isNotEmpty()) articleDao.insertImages(imageEntities)

        val isPartial = imageFailures > 0
        articleDao.updateArticle(
            extractedArticle.copy(
                featuredImageLocalPath = featuredLocalPath,
                downloadState = if (isPartial) "PARTIALLY_SAVED" else "SAVED_OFFLINE",
                downloadError = if (isPartial) "$imageFailures image(s) could not be downloaded." else null,
                lastUpdated = System.currentTimeMillis()
            )
        )
        SaveResult.Success(articleId, isPartial)
    }

    suspend fun updateReadingProgress(id: String, progress: Float, position: Int) {
        articleDao.updateReadingProgress(id, progress, position)
        if (progress >= 0.95f) {
            articleDao.updateReadStatus(id, true)
        }
    }

    suspend fun markAsRead(id: String, isRead: Boolean) {
        articleDao.updateReadStatus(id, isRead)
    }

    suspend fun toggleFavorite(id: String) {
        val current = articleDao.getArticleById(id) ?: return
        articleDao.updateFavoriteStatus(id, !current.isFavorite)
    }

    suspend fun toggleArchive(id: String) {
        val current = articleDao.getArticleById(id) ?: return
        articleDao.updateArchiveStatus(id, !current.isArchived)
    }

    suspend fun assignToFolder(id: String, folderId: String?) {
        articleDao.updateArticleFolder(id, folderId)
    }

    suspend fun updateTtsPosition(id: String, positionMs: Long, paragraphIndex: Int) {
        articleDao.updateTtsPosition(id, positionMs, paragraphIndex)
    }

    suspend fun updateDownloadState(id: String, state: String, error: String? = null) {
        articleDao.updateDownloadState(id, state, error)
    }

    suspend fun deleteArticle(id: String) {
        articleDao.deleteArticleById(id)
    }
}
