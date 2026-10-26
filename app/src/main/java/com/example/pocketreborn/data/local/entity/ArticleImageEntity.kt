package com.example.pocketreborn.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "article_images",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["articleId"])
    ]
)
data class ArticleImageEntity(
    @PrimaryKey
    val id: String,
    val articleId: String,
    val remoteUrl: String,
    val localPath: String? = null,
    val caption: String? = null,
    val isDownloaded: Boolean = false
)
