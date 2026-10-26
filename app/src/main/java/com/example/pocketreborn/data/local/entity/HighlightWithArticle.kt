package com.example.pocketreborn.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class HighlightWithArticle(
    @Embedded val highlight: HighlightEntity,
    @ColumnInfo(name = "articleTitle") val articleTitle: String,
    @ColumnInfo(name = "articlePublisher") val articlePublisher: String?
)
