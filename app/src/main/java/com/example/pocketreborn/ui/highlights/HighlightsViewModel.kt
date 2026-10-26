package com.example.pocketreborn.ui.highlights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketreborn.data.local.entity.HighlightWithArticle
import com.example.pocketreborn.data.repository.HighlightRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HighlightsViewModel(
    private val highlightRepository: HighlightRepository
) : ViewModel() {

    val highlights: StateFlow<List<HighlightWithArticle>> = highlightRepository.getAllHighlightsWithArticle().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun deleteHighlight(id: String) {
        viewModelScope.launch { highlightRepository.deleteHighlight(id) }
    }
}
