package com.example.pocketreborn.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.pocketreborn.R
import com.example.pocketreborn.service.TtsPlaybackState

@Composable
fun TtsBottomBarPlayer(
    state: TtsPlaybackState,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onClose: () -> Unit,
    onOpenReader: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.articleId == null) return

    var showSpeedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onOpenReader(state.articleId) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column {
            // Progress Bar
            val progress = if (state.totalParagraphs > 0) {
                val chunkProgress = if (state.currentChunkLength > 0) {
                    state.currentCharOffset.toFloat() / state.currentChunkLength.toFloat()
                } else 0f
                ((state.currentParagraphIndex + chunkProgress) / state.totalParagraphs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.articleTitle.ifBlank { "Speaking Article..." },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.tts_chunk_progress,
                            state.currentParagraphIndex + 1,
                            state.totalParagraphs,
                            state.speed
                        ) + if (state.offlineVoiceAvailable == false) " • Online voice" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    if (!state.errorMessage.isNullOrBlank()) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    IconButton(onClick = onSkipBackward) {
                        Icon(Icons.Default.FastRewind, contentDescription = stringResource(R.string.tts_previous_chunk))
                    }

                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (state.isPlaying) R.string.tts_pause else R.string.tts_play)
                        )
                    }

                    IconButton(onClick = onSkipForward) {
                        Icon(Icons.Default.FastForward, contentDescription = stringResource(R.string.tts_next_chunk))
                    }

                    Box {
                        IconButton(onClick = { showSpeedMenu = true }) {
                            Icon(Icons.Default.Speed, contentDescription = stringResource(R.string.tts_speech_speed))
                        }

                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.7f, 1.0f, 1.2f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${speed}x",
                                            fontWeight = if (state.speed == speed) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        showSpeedMenu = false
                                        onSetSpeed(speed)
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tts_close_player))
                    }
                }
            }
        }
    }
}
