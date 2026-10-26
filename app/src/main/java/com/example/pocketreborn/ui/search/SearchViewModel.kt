package com.example.pocketreborn.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketreborn.data.local.entity.ArticleEntity
import com.example.pocketreborn.data.repository.ArticleRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: ArticleRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class)
    val searchResults: StateFlow<List<ArticleEntity>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.searchArticles(query.trim())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearQuery() {
        _searchQuery.value = ""
    }

    fun toggleFavorite(id: String) {
        viewModelScope.launch { repository.toggleFavorite(id) }
    }

    fun toggleArchive(id: String) {
        viewModelScope.launch { repository.toggleArchive(id) }
    }

    fun deleteArticle(id: String) {
        viewModelScope.launch { repository.deleteArticle(id) }
    }
}
