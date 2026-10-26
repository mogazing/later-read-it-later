package com.example.pocketreborn.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pocketreborn.data.discovery.DiscoveryArticle
import com.example.pocketreborn.data.discovery.DiscoveryRepository
import com.example.pocketreborn.data.repository.ArticleRepository
import com.example.pocketreborn.data.repository.SaveResult
import com.example.pocketreborn.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI

data class DiscoveryUiState(
    val selectedTopic: String = "All Topics",
    val topics: List<String> = emptyList(),
    val articles: List<DiscoveryArticle> = emptyList(),
    val savedUrls: Set<String> = emptySet(),
    val savedArticleIds: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val lastRefreshAt: Long = 0L,
    val errorMessageRes: Int? = null,
    val messageRes: Int? = null,
    val messageArgument: String? = null,
    val savingUrls: Set<String> = emptySet()
)

class DiscoveryViewModel(
    private val discoveryRepository: DiscoveryRepository,
    private val articleRepository: ArticleRepository,
    private val enqueueDownload: (String) -> Unit
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DiscoveryUiState(topics = discoveryRepository.topics)
    )
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            loadTopic("All Topics")
        }
        viewModelScope.launch {
            articleRepository.getAllArticles().collect { articles ->
                val savedArticleIds = articles
                    .flatMap { article -> listOfNotNull(article.originalUrl, article.canonicalUrl).map { it to article.id } }
                    .associate { (url, id) -> canonicalKey(url) to id }
                _uiState.value = _uiState.value.copy(
                    savedUrls = savedArticleIds.keys,
                    savedArticleIds = savedArticleIds
                )
            }
        }
    }

    fun selectTopic(topic: String) {
        if (_uiState.value.selectedTopic == topic) return
        _uiState.value = _uiState.value.copy(selectedTopic = topic)
        viewModelScope.launch {
            loadTopic(topic)
        }
    }

    private suspend fun loadTopic(topic: String) {
        val cached = discoveryRepository.readCachedTopic(topic)
        _uiState.value = _uiState.value.copy(
            selectedTopic = topic,
            articles = cached.articles,
            lastRefreshAt = cached.refreshedAt,
            isLoading = false
        )
        if (cached.articles.isEmpty()) {
            refreshTopic(topic)
        }
    }

    fun refresh() {
        refreshTopic(_uiState.value.selectedTopic)
    }

    private fun refreshTopic(topic: String) {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.value = state.copy(
                isLoading = state.articles.isEmpty(),
                isRefreshing = true,
                errorMessageRes = null
            )
            try {
                val previousUrls = state.articles.map(::canonicalKey).toSet()
                val recommendations = discoveryRepository.fetchTopicBatch(topic, previousUrls)
                val refreshedAt = System.currentTimeMillis()
                discoveryRepository.saveTopicBatch(topic, recommendations, refreshedAt)
                if (_uiState.value.selectedTopic == topic) {
                    _uiState.value = _uiState.value.copy(
                        articles = recommendations,
                        lastRefreshAt = refreshedAt,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessageRes = null
                    )
                }
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessageRes = if (_uiState.value.articles.isEmpty()) {
                        R.string.discovery_refresh_error_empty
                    } else {
                        R.string.discovery_refresh_error_cached
                    }
                )
            }
        }
    }

    fun bookmark(article: DiscoveryArticle) {
        saveArticle(article)
    }

    fun openArticle(article: DiscoveryArticle, onOpen: (String) -> Unit) {
        saveArticle(article, onOpen)
    }

    private fun saveArticle(article: DiscoveryArticle, onSaved: ((String) -> Unit)? = null) {
        val key = canonicalKey(article)
        val state = _uiState.value
        if (key in state.savedUrls) {
            _uiState.value = state.copy(messageRes = R.string.article_already_in_library)
            state.savedArticleIds[key]?.let { onSaved?.invoke(it) }
            return
        }
        if (key in state.savingUrls) {
            _uiState.value = state.copy(messageRes = R.string.article_already_saving)
            return
        }
        _uiState.value = state.copy(savingUrls = state.savingUrls + key, messageRes = null, messageArgument = null)
        viewModelScope.launch {
            try {
                when (
                    val result = articleRepository.queueDiscoveryArticle(
                        url = article.url,
                        title = article.title,
                        publisher = article.publisher,
                        excerpt = article.description,
                        publishedDate = article.publishedAt,
                        imageUrl = article.imageUrl
                    )
                ) {
                    is SaveResult.Queued -> {
                        enqueueDownload(result.articleId)
                        _uiState.value = _uiState.value.copy(messageRes = R.string.saved_article_queued, messageArgument = null)
                        onSaved?.invoke(result.articleId)
                    }
                    is SaveResult.ExistingDuplicate -> {
                        _uiState.value = _uiState.value.copy(messageRes = R.string.article_already_in_library, messageArgument = null)
                        onSaved?.invoke(result.articleId)
                    }
                    is SaveResult.Error -> {
                        _uiState.value = _uiState.value.copy(messageRes = R.string.could_not_save_article, messageArgument = result.message)
                    }
                    is SaveResult.Success -> {
                        _uiState.value = _uiState.value.copy(messageRes = R.string.article_already_in_library, messageArgument = null)
                    }
                }
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(messageRes = R.string.could_not_save_article, messageArgument = error.message ?: "unknown error")
            } finally {
                _uiState.value = _uiState.value.copy(savingUrls = _uiState.value.savingUrls - key)
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null, messageArgument = null)
    }

    private fun canonicalKey(article: DiscoveryArticle): String = canonicalKey(article.url)

    private fun canonicalKey(url: String): String = runCatching {
        val uri = URI(url)
        val query = uri.rawQuery.orEmpty().split("&")
            .filterNot { it.substringBefore("=").lowercase() in setOf("ref", "source", "fbclid", "gclid") ||
                it.substringBefore("=").lowercase().startsWith("utm_") }
            .joinToString("&")
            .takeIf(String::isNotBlank)
        URI(uri.scheme.lowercase(), uri.authority.lowercase(), uri.path.trimEnd('/'), query, null).toString()
    }.getOrDefault(url.substringBefore("#").trimEnd('/'))
}
