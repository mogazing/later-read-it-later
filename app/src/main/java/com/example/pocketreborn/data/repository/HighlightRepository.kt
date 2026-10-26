package com.example.pocketreborn.data.repository

import com.example.pocketreborn.data.local.dao.HighlightDao
import com.example.pocketreborn.data.local.entity.HighlightEntity
import com.example.pocketreborn.data.local.entity.HighlightWithArticle
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class HighlightRepository(private val highlightDao: HighlightDao) {

    fun getHighlightsForArticle(articleId: String): Flow<List<HighlightEntity>> =
        highlightDao.getHighlightsForArticle(articleId)

    fun getAllHighlights(): Flow<List<HighlightEntity>> = highlightDao.getAllHighlights()
    fun getAllHighlightsWithArticle(): Flow<List<HighlightWithArticle>> = highlightDao.getAllHighlightsWithArticle()

    suspend fun addHighlight(
        articleId: String,
        highlightedText: String,
        prefixContext: String? = null,
        suffixContext: String? = null,
        startOffset: Int = 0,
        endOffset: Int = 0,
        note: String? = null,
        colorHex: String = "#FFE082"
    ): HighlightEntity {
        val highlight = HighlightEntity(
            id = UUID.randomUUID().toString(),
            articleId = articleId,
            highlightedText = highlightedText,
            prefixContext = prefixContext,
            suffixContext = suffixContext,
            startOffset = startOffset,
            endOffset = endOffset,
            note = note?.trim(),
            createdAt = System.currentTimeMillis(),
            colorHex = colorHex
        )
        highlightDao.insertHighlight(highlight)
        return highlight
    }

    suspend fun updateNote(highlight: HighlightEntity, note: String?) {
        highlightDao.updateHighlight(highlight.copy(note = note?.trim()))
    }

    suspend fun deleteHighlight(id: String) {
        highlightDao.deleteHighlightById(id)
    }
}
