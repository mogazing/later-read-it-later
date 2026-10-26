package com.example.pocketreborn.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import coil.compose.AsyncImage
import com.example.pocketreborn.data.network.NetworkSecurityUtils
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    articleId: String,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onStartTts: (articleId: String, title: String, publisher: String, text: String) -> Unit,
    onSaveUrl: (String) -> Unit = {},
    startOffset: Int = -1
) {
    BackHandler {
        onBack()
    }

    LaunchedEffect(articleId) {
        viewModel.loadArticle(articleId)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showFontMenu by remember { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val textBringIntoViewRequester = remember { BringIntoViewRequester() }

    val article = state.article
    var didApplyInitialOffset by remember(article?.id, startOffset) { mutableStateOf(false) }
    val highlightToNavigate by viewModel.highlightToNavigate.collectAsState()
    var highlightScrollOffset by remember(article?.id) { mutableStateOf<Int?>(null) }
    var articleTextFieldValue by remember(article?.id, article?.plainText) {
        mutableStateOf(TextFieldValue(article?.plainText.orEmpty()))
    }

    var selectedLinkUrl by remember { mutableStateOf<String?>(null) }

    val articleLinks = remember(article?.content) {
        if (article?.content.isNullOrBlank()) emptyList() else {
            runCatching {
                val doc = Jsoup.parse(article?.content.orEmpty())
                doc.select("a[href]").mapNotNull { a ->
                    val href = a.attr("abs:href").ifBlank { a.attr("href") }
                    val text = a.text().trim().ifBlank { href }
                    if (NetworkSecurityUtils.isValidWebUrl(href)) href to text else null
                }.distinctBy { it.first }
            }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(article?.id, highlightToNavigate) {
        val loadedArticle = article ?: return@LaunchedEffect
        val highlight = highlightToNavigate ?: return@LaunchedEffect
        if (highlight.articleId != loadedArticle.id) return@LaunchedEffect
        highlightScrollOffset = findHighlightOffset(loadedArticle.plainText, highlight)
        viewModel.clearHighlightNavigation()
    }

    val targetOffset = if (startOffset >= 0) startOffset else highlightScrollOffset
    LaunchedEffect(article?.id, targetOffset, textLayoutResult) {
        val loadedArticle = article ?: return@LaunchedEffect
        val layout = textLayoutResult ?: return@LaunchedEffect
        val offset = targetOffset ?: return@LaunchedEffect
        if (!didApplyInitialOffset && offset in 0 until loadedArticle.plainText.length) {
            val line = layout.getLineForOffset(offset)
            didApplyInitialOffset = true
            textBringIntoViewRequester.bringIntoView(
                Rect(
                    left = 0f,
                    top = layout.getLineTop(line),
                    right = layout.size.width.toFloat(),
                    bottom = layout.getLineBottom(line)
                )
            )
        }
    }

    fun copyFullTextToClipboard() {
        val textToCopy = article?.plainText.orEmpty()
        if (textToCopy.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Article Text", textToCopy)
            clipboard.setPrimaryClip(clip)
            scope.launch {
                snackbarHostState.showSnackbar("Full article text copied to clipboard!")
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("No article text available to copy.")
            }
        }
    }

    // Track scroll position
    LaunchedEffect(scrollState.value) {
        if (scrollState.maxValue > 0) {
            val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
            viewModel.updateReadingProgress(progress, scrollState.value)
        }
    }

    if (selectedLinkUrl != null) {
        val url = selectedLinkUrl.orEmpty()
        AlertDialog(
            onDismissRequest = { selectedLinkUrl = null },
            title = { Text("Article Link Options") },
            text = {
                Column {
                    Text("Selected link:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = selectedLinkUrl
                        selectedLinkUrl = null
                        if (!target.isNullOrBlank()) {
                            onSaveUrl(target)
                            scope.launch {
                                snackbarHostState.showSnackbar("Link queued to your Later reading list!")
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save to Later")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            val target = selectedLinkUrl
                            selectedLinkUrl = null
                            if (!target.isNullOrBlank()) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            }
                        }
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open in Browser")
                    }

                    TextButton(onClick = { selectedLinkUrl = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (article != null) {
                        if (!articleTextFieldValue.selection.collapsed) {
                            TextButton(onClick = {
                                val selectionStart = minOf(
                                    articleTextFieldValue.selection.start,
                                    articleTextFieldValue.selection.end
                                )
                                val selectionEnd = maxOf(
                                    articleTextFieldValue.selection.start,
                                    articleTextFieldValue.selection.end
                                )
                                val selectedText = article.plainText.substring(selectionStart, selectionEnd)
                                viewModel.addHighlight(selectedText, selectionStart, selectionEnd)
                                articleTextFieldValue = articleTextFieldValue.copy(
                                    selection = TextRange(selectionEnd)
                                )
                            }) {
                                Text("Highlight")
                            }
                        }

                        IconButton(onClick = { copyFullTextToClipboard() }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Full Text")
                        }

                        IconButton(onClick = {
                            Log.d("LaterTTS", "Play/Headphones button clicked in ReaderScreen for articleId='${article.id}', title='${article.title}', textLength=${article.plainText.length}")
                            onStartTts(
                                article.id,
                                article.title,
                                article.publisher ?: "Web Article",
                                article.plainText
                            )
                        }) {
                            Icon(Icons.Default.Headphones, contentDescription = "Listen with Text to Speech")
                        }

                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (article.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (article.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }

                        IconButton(onClick = { showFontMenu = true }) {
                            Icon(Icons.Default.FormatSize, contentDescription = "Appearance Settings")
                        }

                        DropdownMenu(
                            expanded = showFontMenu,
                            onDismissRequest = { showFontMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy Full Text") },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                onClick = {
                                    showFontMenu = false
                                    copyFullTextToClipboard()
                                }
                            )

                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Font Size: ${state.preferences.fontSizeSp} sp", style = MaterialTheme.typography.titleSmall)
                                Slider(
                                    value = state.preferences.fontSizeSp.toFloat(),
                                    onValueChange = { viewModel.updateFontSize(it.toInt()) },
                                    valueRange = 12f..32f
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Line Spacing", style = MaterialTheme.typography.titleSmall)
                                Row {
                                    listOf(1.2f, 1.4f, 1.6f, 1.8f).forEach { spacing ->
                                        IconButton(onClick = { viewModel.updateLineSpacing(spacing) }) {
                                            Text("${spacing}x", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading || article == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val fontFamily = when (state.preferences.fontFamily) {
                "SERIF" -> FontFamily.Serif
                "MONOSPACE" -> FontFamily.Monospace
                "SANS_SERIF" -> FontFamily.SansSerif
                else -> FontFamily.Default
            }

            val fontSize = state.preferences.fontSizeSp.sp
            val lineSpacing = (state.preferences.fontSizeSp * state.preferences.lineSpacingMultiplier).sp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Publisher & Date
                Text(
                    text = buildString {
                        append(article.publisher ?: "Web Article")
                        if (!article.author.isNullOrBlank()) append(" • ").append(article.author)
                        if (!article.publishedDate.isNullOrBlank()) append(" • ").append(article.publishedDate)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Title
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${article.estimatedReadingTimeMinutes} min read",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Featured Image
                val imageModel = article.featuredImageLocalPath?.let { File(it) } ?: article.featuredImageUrl
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Featured image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                BasicTextField(
                    value = articleTextFieldValue,
                    onValueChange = { newValue ->
                        if (newValue.text == article.plainText) articleTextFieldValue = newValue
                    },
                    readOnly = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = fontSize,
                        lineHeight = lineSpacing,
                        fontFamily = fontFamily
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    onTextLayout = { textLayoutResult = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(textBringIntoViewRequester)
                )

                if (articleLinks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "Interactive Article Links",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    articleLinks.take(10).forEach { (url, label) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedLinkUrl = url },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
