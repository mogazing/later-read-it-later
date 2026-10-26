package com.example.pocketreborn.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pocketreborn.data.local.entity.HighlightEntity
import com.example.pocketreborn.data.local.entity.HighlightWithArticle
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE articleId = :articleId ORDER BY createdAt DESC")
    fun getHighlightsForArticle(articleId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights ORDER BY createdAt DESC")
    fun getAllHighlights(): Flow<List<HighlightEntity>>

    @Query(
        "SELECT highlights.*, articles.title AS articleTitle, articles.publisher AS articlePublisher " +
            "FROM highlights INNER JOIN articles ON highlights.articleId = articles.id " +
            "ORDER BY highlights.createdAt DESC"
    )
    fun getAllHighlightsWithArticle(): Flow<List<HighlightWithArticle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: HighlightEntity)

    @Update
    suspend fun updateHighlight(highlight: HighlightEntity)

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlightById(id: String)
}
