package com.example.pocketreborn.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketreborn.data.local.entity.ArticleEntity
import com.example.pocketreborn.data.local.entity.FolderEntity
import com.example.pocketreborn.data.repository.ArticleRepository
import com.example.pocketreborn.data.repository.FolderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoldersViewModel(
    private val folderRepository: FolderRepository,
    private val articleRepository: ArticleRepository
) : ViewModel() {

    val folders: StateFlow<List<FolderEntity>> = folderRepository.getAllFolders().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedFolderArticles: StateFlow<List<ArticleEntity>> = _selectedFolderId
        .flatMapLatest { folderId ->
            if (folderId == null) {
                flowOf(emptyList())
            } else {
                articleRepository.getArticlesByFolder(folderId)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            folderRepository.createFolder(name)
        }
    }

    fun selectFolder(folderId: String?) {
        _selectedFolderId.value = folderId
    }

    fun renameFolder(id: String, newName: String) {
        viewModelScope.launch {
            folderRepository.renameFolder(id, newName)
        }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            if (_selectedFolderId.value == id) {
                _selectedFolderId.value = null
            }
            folderRepository.deleteFolder(id)
        }
    }
}
