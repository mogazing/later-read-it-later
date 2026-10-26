package com.example.pocketreborn.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketreborn.data.local.entity.ArticleEntity
import com.example.pocketreborn.data.local.entity.HighlightEntity
import com.example.pocketreborn.data.repository.ArticleRepository
import com.example.pocketreborn.data.repository.HighlightRepository
import com.example.pocketreborn.data.repository.UserPreferences
import com.example.pocketreborn.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReaderUiState(
    val article: ArticleEntity? = null,
    val highlights: List<HighlightEntity> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = true
)

class ReaderViewModel(
    private val articleRepository: ArticleRepository,
    private val highlightRepository: HighlightRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _articleId = MutableStateFlow<String?>(null)
    private val _highlightToNavigate = MutableStateFlow<HighlightEntity?>(null)
    val highlightToNavigate: StateFlow<HighlightEntity?> = _highlightToNavigate

    val uiState: StateFlow<ReaderUiState> = _articleId
        .filterNotNull()
        .flatMapLatest { id ->
            combine(
                articleRepository.getArticleByIdFlow(id),
                highlightRepository.getHighlightsForArticle(id),
                userPreferencesRepository.userPreferencesFlow
            ) { article, highlights, prefs ->
                ReaderUiState(
                    article = article,
                    highlights = highlights,
                    preferences = prefs,
                    isLoading = false
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReaderUiState()
        )

    fun loadArticle(id: String) {
        _articleId.value = id
        viewModelScope.launch {
            articleRepository.markAsOpened(id)
        }
    }

    fun requestHighlightNavigation(highlight: HighlightEntity) {
        _highlightToNavigate.value = highlight
    }

    fun clearHighlightNavigation() {
        _highlightToNavigate.value = null
    }

    fun updateReadingProgress(progress: Float, position: Int) {
        val id = _articleId.value ?: return
        viewModelScope.launch {
            articleRepository.updateReadingProgress(id, progress, position)
        }
    }

    fun addHighlight(text: String, startOffset: Int, endOffset: Int, note: String? = null) {
        val id = _articleId.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            val article = articleRepository.getArticleById(id)
            val passageMatchesOffsets = article != null &&
                startOffset >= 0 &&
                endOffset <= article.plainText.length &&
                startOffset <= endOffset &&
                article.plainText.substring(startOffset, endOffset) == text
            val safeStart = if (passageMatchesOffsets) startOffset else 0
            val safeEnd = if (passageMatchesOffsets) endOffset else 0
            val sourceText = article?.plainText.orEmpty()
            val prefix = sourceText.substring(maxOf(0, safeStart - 50), safeStart)
            val suffix = sourceText.substring(safeEnd, minOf(sourceText.length, safeEnd + 50))
            highlightRepository.addHighlight(
                articleId = id,
                highlightedText = text,
                prefixContext = prefix,
                suffixContext = suffix,
                startOffset = safeStart,
                endOffset = safeEnd,
                note = note
            )
        }
    }

    fun updateFontSize(sizeSp: Int) {
        viewModelScope.launch { userPreferencesRepository.updateFontSize(sizeSp) }
    }

    fun updateLineSpacing(spacing: Float) {
        viewModelScope.launch { userPreferencesRepository.updateLineSpacing(spacing) }
    }

    fun updateFontFamily(family: String) {
        viewModelScope.launch { userPreferencesRepository.updateFontFamily(family) }
    }

    fun toggleFavorite() {
        val id = _articleId.value ?: return
        viewModelScope.launch { articleRepository.toggleFavorite(id) }
    }

    fun toggleArchive() {
        val id = _articleId.value ?: return
        viewModelScope.launch { articleRepository.toggleArchive(id) }
    }
}
