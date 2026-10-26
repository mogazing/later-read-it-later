package com.example.pocketreborn.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "highlights",
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
data class HighlightEntity(
    @PrimaryKey
    val id: String,
    val articleId: String,
    val highlightedText: String,
    val prefixContext: String? = null,
    val suffixContext: String? = null,
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val colorHex: String = "#FFE082"
)
