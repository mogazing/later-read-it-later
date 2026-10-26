package com.example.pocketreborn.ui.library

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketreborn.data.local.entity.ArticleEntity
import com.example.pocketreborn.data.repository.ArticleRepository
import com.example.pocketreborn.data.repository.SaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter {
    ALL, FAVORITES, ARCHIVED
}

enum class SortOrder {
    NEWEST, OLDEST, READING_TIME
}

data class LibraryUiState(
    val articles: List<ArticleEntity> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val isSaving: Boolean = false,
    val saveErrorMessage: String? = null,
    val saveSuccessMessage: String? = null,
    val isSelectionMode: Boolean = false,
    val selectedArticleIds: Set<String> = emptySet()
)

class LibraryViewModel(
    private val repository: ArticleRepository,
    private val enqueueDownload: (String) -> Unit
) : ViewModel() {

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    private val _isSaving = MutableStateFlow(false)
    private val _saveError = MutableStateFlow<String?>(null)
    private val _saveSuccess = MutableStateFlow<String?>(null)
    private val _selectedArticleIds = MutableStateFlow<Set<String>>(emptySet())

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<LibraryUiState> = combine(
        _filter,
        _sortOrder,
        _isSaving,
        _saveError,
        _saveSuccess,
        _selectedArticleIds,
        repository.getAllActiveArticles(),
        repository.getFavoriteArticles(),
        repository.getArchivedArticles()
    ) { flows: Array<Any?> ->
        val filter = flows[0] as LibraryFilter
        val sort = flows[1] as SortOrder
        val saving = flows[2] as Boolean
        val error = flows[3] as String?
        val success = flows[4] as String?
        val selected = flows[5] as Set<String>
        val active = flows[6] as List<ArticleEntity>
        val favorites = flows[7] as List<ArticleEntity>
        val archived = flows[8] as List<ArticleEntity>

        val filteredList = when (filter) {
            LibraryFilter.ALL -> active
            LibraryFilter.FAVORITES -> favorites
            LibraryFilter.ARCHIVED -> archived
        }

        val sortedList = when (sort) {
            SortOrder.NEWEST -> filteredList.sortedByDescending { it.dateSaved }
            SortOrder.OLDEST -> filteredList.sortedBy { it.dateSaved }
            SortOrder.READING_TIME -> filteredList.sortedBy { it.estimatedReadingTimeMinutes }
        }

        LibraryUiState(
            articles = sortedList,
            filter = filter,
            sortOrder = sort,
            isSaving = saving,
            saveErrorMessage = error,
            saveSuccessMessage = success,
            isSelectionMode = selected.isNotEmpty(),
            selectedArticleIds = selected
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
        clearSelection()
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _sortOrder.value = sortOrder
    }

    fun startSelectionMode(articleId: String) {
        _selectedArticleIds.value = setOf(articleId)
    }

    fun toggleArticleSelection(articleId: String) {
        val current = _selectedArticleIds.value
        _selectedArticleIds.value = if (current.contains(articleId)) {
            current - articleId
        } else {
            current + articleId
        }
    }

    fun selectAll() {
        val currentArticles = uiState.value.articles.map { it.id }.toSet()
        _selectedArticleIds.value = currentArticles
    }

    fun clearSelection() {
        _selectedArticleIds.value = emptySet()
    }

    fun deleteSelectedArticles() {
        val idsToDelete = _selectedArticleIds.value
        if (idsToDelete.isEmpty()) return
        viewModelScope.launch {
            idsToDelete.forEach { id ->
                repository.deleteArticle(id)
            }
            val count = idsToDelete.size
            _saveSuccess.value = if (count == 1) "1 article deleted." else "$count articles deleted."
            clearSelection()
        }
    }

    fun addTestArticle() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                when (val result = repository.insertTestArticle()) {
                    is SaveResult.Success -> {
                        _saveSuccess.value = "TTS Test Article added to your library!"
                    }
                    is SaveResult.ExistingDuplicate -> {
                        _saveSuccess.value = "TTS Test Article is already in your library."
                    }
                    else -> {
                        _saveError.value = "Failed to add TTS Test Article."
                    }
                }
            } catch (e: Exception) {
                _saveError.value = "Could not add test article: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveUrl(url: String) {
        viewModelScope.launch {
            _isSaving.value = true
            _saveError.value = null
            _saveSuccess.value = null

            try {
                when (val result = repository.queueArticleUrl(url)) {
                    is SaveResult.Queued -> {
                        enqueueDownload(result.articleId)
                        _saveSuccess.value = "Link queued. It will be saved when an internet connection is available."
                    }
                    is SaveResult.ExistingDuplicate -> {
                        val existing = repository.getArticleById(result.articleId)
                        if (existing != null && existing.downloadState !in setOf("SAVED_OFFLINE", "PARTIALLY_SAVED")) {
                            repository.updateDownloadState(result.articleId, "QUEUED")
                            enqueueDownload(result.articleId)
                            _saveSuccess.value = "This link is already queued and will be retried."
                        } else {
                            _saveSuccess.value = "Article is already in your library."
                        }
                    }
                    is SaveResult.Error -> _saveError.value = result.message
                    is SaveResult.Success -> _saveError.value = "Unexpected result while queuing the link."
                }
            } catch (e: SQLiteException) {
                _saveError.value = "Could not queue this link in the local library: ${e.message ?: "database error"}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun reportSaveError(message: String) {
        _saveError.value = message
    }

    fun retryArticle(id: String) {
        viewModelScope.launch {
            val article = repository.getArticleById(id) ?: return@launch
            if (article.downloadState == "SAVED_OFFLINE" || article.downloadState == "PARTIALLY_SAVED") return@launch
            repository.updateDownloadState(id, "QUEUED")
            enqueueDownload(id)
            _saveSuccess.value = "Retry queued. The article will be saved when a connection is available."
        }
    }

    fun clearMessages() {
        _saveError.value = null
        _saveSuccess.value = null
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }

    fun toggleArchive(id: String) {
        viewModelScope.launch { repository.toggleArchive(id) }
    }

    fun toggleRead(id: String) {
        viewModelScope.launch {
            val article = repository.getArticleById(id) ?: return@launch
            repository.markAsRead(id, !article.isRead)
        }
    }

    fun assignFolder(id: String, folderId: String?) {
        viewModelScope.launch { repository.assignToFolder(id, folderId) }
    }

    fun deleteArticle(id: String) {
        viewModelScope.launch { repository.deleteArticle(id) }
    }
}
