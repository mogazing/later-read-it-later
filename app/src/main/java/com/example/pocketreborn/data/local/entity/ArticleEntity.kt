package com.example.pocketreborn.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["originalUrl"], unique = true),
        Index(value = ["canonicalUrl"]),
        Index(value = ["folderId"])
    ]
)
data class ArticleEntity(
    @PrimaryKey
    val id: String,
    val originalUrl: String,
    val canonicalUrl: String? = null,
    val title: String,
    val author: String? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val content: String, // HTML or structured content
    val plainText: String, // Clean text for TTS & FTS
    val excerpt: String? = null,
    val featuredImageUrl: String? = null,
    val featuredImageLocalPath: String? = null,
    val dateSaved: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val downloadState: String, // RESOLVING, FETCHING, EXTRACTING, SAVED_OFFLINE, PARTIALLY_SAVED, BOOKMARK_ONLY, FAILED
    val downloadError: String? = null,
    val isRead: Boolean = false,
    val isOpened: Boolean = false,
    val readingProgress: Float = 0.0f,
    val lastReadPosition: Int = 0,
    val estimatedReadingTimeMinutes: Int = 1,
    val folderId: String? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val ttsPositionMs: Long = 0L,
    val ttsParagraphIndex: Int = 0
)
