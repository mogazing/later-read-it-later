package com.example.pocketreborn.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.pocketreborn.R
import com.example.pocketreborn.ui.components.AddUrlDialog
import com.example.pocketreborn.ui.components.ArticleCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenArticle: (String) -> Unit,
    onOpenSearch: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.saveSuccessMessage, state.saveErrorMessage) {
        state.saveSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
        state.saveErrorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    if (showBulkDeleteDialog) {
        val count = state.selectedArticleIds.size
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text(if (count == 1) "Delete 1 Article?" else "Delete $count Articles?") },
            text = { Text("This will permanently remove the selected articles and their saved files from your library.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBulkDeleteDialog = false
                        viewModel.deleteSelectedArticles()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${state.selectedArticleIds.size} Selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { showBulkDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = "Later",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_saved_articles))
                        }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Library Options")
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add TTS Test Article") },
                                leadingIcon = { Icon(Icons.Default.Article, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.addTestArticle()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort: Newest First") },
                                leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.NEWEST)
                                    showMoreMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort: Oldest First") },
                                leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.OLDEST)
                                    showMoreMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sort: Reading Time") },
                                leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                                onClick = {
                                    viewModel.setSortOrder(SortOrder.READING_TIME)
                                    showMoreMenu = false
                                }
                            )
                        }
                    },
                    windowInsets = TopAppBarDefaults.windowInsets
                )
            }
        },
        floatingActionButton = {
            if (!state.isSelectionMode) {
                SmallFloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 76.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Save New URL")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Tabs
            PrimaryTabRow(selectedTabIndex = state.filter.ordinal) {
                Tab(
                    selected = state.filter == LibraryFilter.ALL,
                    onClick = { viewModel.setFilter(LibraryFilter.ALL) },
                    text = { Text(stringResource(R.string.library_my_list)) }
                )
                Tab(
                    selected = state.filter == LibraryFilter.FAVORITES,
                    onClick = { viewModel.setFilter(LibraryFilter.FAVORITES) },
                    text = { Text(stringResource(R.string.library_favorites)) }
                )
                Tab(
                    selected = state.filter == LibraryFilter.ARCHIVED,
                    onClick = { viewModel.setFilter(LibraryFilter.ARCHIVED) },
                    text = { Text(stringResource(R.string.library_archive)) }
                )
            }

            if (state.articles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Article,
                            contentDescription = null,
                            modifier = Modifier.height(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when (state.filter) {
                                LibraryFilter.ALL -> "Your library is empty."
                                LibraryFilter.FAVORITES -> "No favorite articles saved yet."
                                LibraryFilter.ARCHIVED -> "No archived articles."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Share links from Chrome or other apps to Later, or tap '+' to paste an article link.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.addTestArticle() }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add TTS Test Article")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = state.articles,
                        key = { it.id }
                    ) { article ->
                        val isSelected = state.selectedArticleIds.contains(article.id)
                        ArticleCard(
                            article = article,
                            isSelectionMode = state.isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.toggleArticleSelection(article.id)
                                } else {
                                    onOpenArticle(article.id)
                                }
                            },
                            onLongClick = {
                                if (!state.isSelectionMode) {
                                    viewModel.startSelectionMode(article.id)
                                } else {
                                    viewModel.toggleArticleSelection(article.id)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(article.id) },
                            onToggleArchive = { viewModel.toggleArchive(article.id) },
                            onToggleRead = { viewModel.toggleRead(article.id) },
                            onAssignFolder = { /* Show folder dialog if needed */ },
                            onDelete = { viewModel.deleteArticle(article.id) },
                            onRetry = { viewModel.retryArticle(article.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddUrlDialog(
            onDismiss = { showAddDialog = false },
            onSaveUrl = { url ->
                viewModel.saveUrl(url)
                showAddDialog = false
            },
            isSaving = state.isSaving,
            errorMessage = state.saveErrorMessage
        )
    }
}
