package com.example.pocketreborn.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pocketreborn.data.local.entity.ArticleEntity
import com.example.pocketreborn.data.local.entity.ArticleImageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Query("SELECT * FROM articles WHERE isArchived = 0 ORDER BY dateSaved DESC")
    fun getAllActiveArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isArchived = 1 ORDER BY dateSaved DESC")
    fun getArchivedArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isFavorite = 1 AND isArchived = 0 ORDER BY dateSaved DESC")
    fun getFavoriteArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE folderId = :folderId AND isArchived = 0 ORDER BY dateSaved DESC")
    fun getArticlesByFolder(folderId: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleById(id: String): ArticleEntity?

    @Query(
        "SELECT id FROM articles WHERE downloadState IN " +
            "('QUEUED', 'RETRY_PENDING', 'RESOLVING', 'FETCHING', 'EXTRACTING', 'DOWNLOADING_IMAGES')"
    )
    suspend fun getPendingDownloadIds(): List<String>

    @Query("SELECT * FROM articles WHERE id = :id")
    fun getArticleByIdFlow(id: String): Flow<ArticleEntity?>

    @Query("SELECT * FROM articles WHERE originalUrl = :url OR canonicalUrl = :url LIMIT 1")
    suspend fun getArticleByUrl(url: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE title LIKE '%' || :query || '%' OR plainText LIKE '%' || :query || '%' OR publisher LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%'")
    fun searchArticles(query: String): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticle(article: ArticleEntity)

    @Update
    suspend fun updateArticle(article: ArticleEntity)

    @Query("UPDATE articles SET downloadState = :downloadState, downloadError = :error, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateDownloadState(id: String, downloadState: String, error: String? = null, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE articles SET readingProgress = :progress, lastReadPosition = :position, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateReadingProgress(id: String, progress: Float, position: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE articles SET isRead = :isRead, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateReadStatus(id: String, isRead: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE articles SET isOpened = 1, lastUpdated = :timestamp WHERE id = :id")
    suspend fun markAsOpened(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE articles SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)

    @Query("UPDATE articles SET isArchived = :isArchived WHERE id = :id")
    suspend fun updateArchiveStatus(id: String, isArchived: Boolean)

    @Query("UPDATE articles SET folderId = :folderId WHERE id = :id")
    suspend fun updateArticleFolder(id: String, folderId: String?)

    @Query("UPDATE articles SET ttsPositionMs = :positionMs, ttsParagraphIndex = :paragraphIndex WHERE id = :id")
    suspend fun updateTtsPosition(id: String, positionMs: Long, paragraphIndex: Int)

    @Delete
    suspend fun deleteArticle(article: ArticleEntity)

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteArticleById(id: String)

    // Images
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ArticleImageEntity>)

    @Query("SELECT * FROM article_images WHERE articleId = :articleId")
    suspend fun getImagesForArticle(articleId: String): List<ArticleImageEntity>

    @Query("SELECT * FROM article_images WHERE articleId = :articleId")
    fun getImagesForArticleFlow(articleId: String): Flow<List<ArticleImageEntity>>

    @Query("UPDATE article_images SET localPath = :localPath, isDownloaded = :isDownloaded WHERE id = :id")
    suspend fun updateImageDownloadState(id: String, localPath: String?, isDownloaded: Boolean)
}
