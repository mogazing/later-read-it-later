package com.example.pocketreborn.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pocketreborn.data.local.entity.ArticleEntity
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleCard(
    article: ArticleEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleRead: () -> Unit,
    onAssignFolder: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit = {},
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    // Keep previously opened articles visually muted.
    val titleColor = if (article.isOpened) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val subtitleColor = if (article.isOpened) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        article.isOpened -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val formattedDate = remember(article.publishedDate) {
        formatPublishedDate(article.publishedDate)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Prefer an existing local image; otherwise use the remote URL.
            val imageModel = article.featuredImageLocalPath
                ?.takeIf { it.isNotBlank() }
                ?.let { path -> File(path).takeIf { it.exists() } }
                ?: article.featuredImageUrl?.takeIf { it.isNotBlank() }

            // Large image above text (Screenshot 1 layout)
            if (imageModel != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Featured image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .alpha(if (article.isOpened) 0.65f else 1.0f),
                        contentScale = ContentScale.Crop
                    )

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // Publisher and friendly formatted date (no raw ISO timestamps)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append(article.publisher ?: "Web Article")
                        if (!formattedDate.isNullOrBlank()) {
                            append(" • ")
                            append(formattedDate)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isSelected && imageModel == null) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (article.isOpened) FontWeight.Medium else FontWeight.Bold,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Excerpt
            if (!article.excerpt.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = article.excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reading progress bar
            if (article.readingProgress in 0.05f..0.95f) {
                LinearProgressIndicator(
                    progress = { article.readingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Bottom actions and badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${article.estimatedReadingTimeMinutes} min read",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    DownloadStateBadge(state = article.downloadState)
                }

                if (!isSelectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (article.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (article.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }

                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (article.downloadState in setOf("FAILED", "RETRY_PENDING", "BOOKMARK_ONLY")) {
                                DropdownMenuItem(
                                    text = { Text("Retry saving") },
                                    onClick = {
                                        showMenu = false
                                        onRetry()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text(if (article.isRead) "Mark as Unread" else "Mark as Read") },
                                onClick = {
                                    showMenu = false
                                    onToggleRead()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (article.isRead) Icons.Outlined.CheckCircle else Icons.Default.CheckCircle,
                                        contentDescription = null
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text(if (article.isArchived) "Unarchive" else "Archive") },
                                onClick = {
                                    showMenu = false
                                    onToggleArchive()
                                },
                                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) }
                            )

                            DropdownMenuItem(
                                text = { Text("Organize Folder") },
                                onClick = {
                                    showMenu = false
                                    onAssignFolder()
                                },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                            )

                            DropdownMenuItem(
                                text = { Text("Delete Article") },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Download error details
            if (article.downloadState in setOf("FAILED", "RETRY_PENDING", "BOOKMARK_ONLY") &&
                !article.downloadError.isNullOrBlank()
            ) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = article.downloadError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DownloadStateBadge(state: String) {
    val (label, color) = when (state) {
        "SAVED_OFFLINE" -> "Offline" to MaterialTheme.colorScheme.primary
        "PARTIALLY_SAVED" -> "Partial" to MaterialTheme.colorScheme.tertiary
        "BOOKMARK_ONLY" -> "Bookmark" to MaterialTheme.colorScheme.secondary
        "QUEUED", "RETRY_PENDING" -> "Queued" to MaterialTheme.colorScheme.tertiary
        "FETCHING", "EXTRACTING", "DOWNLOADING_IMAGES", "RESOLVING" -> "Saving..." to MaterialTheme.colorScheme.outline
        "FAILED" -> "Needs attention" to MaterialTheme.colorScheme.error
        else -> "Queued" to MaterialTheme.colorScheme.tertiary
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatPublishedDate(rawDate: String?): String? {
    if (rawDate.isNullOrBlank()) return null
    val trimmed = rawDate.trim()

    return runCatching {
        if (trimmed.contains("T") || trimmed.contains("+") || trimmed.contains("Z") || trimmed.matches(Regex(".*\\d{2}:\\d{2}.*"))) {
            val instant = runCatching { Instant.parse(trimmed) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(trimmed).toInstant() }.getOrNull()
                ?: runCatching { ZonedDateTime.parse(trimmed, DateTimeFormatter.ISO_DATE_TIME).toInstant() }.getOrNull()

            if (instant != null) {
                val zdt = instant.atZone(ZoneId.systemDefault())
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(zdt)
            } else {
                val datePart = trimmed.substringBefore("T")
                if (datePart.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    val ld = LocalDate.parse(datePart)
                    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(ld)
                } else null
            }
        } else if (trimmed.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            val ld = LocalDate.parse(trimmed)
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(ld)
        } else if (trimmed.length > 25) {
            null
        } else {
            trimmed
        }
    }.getOrNull()
}
