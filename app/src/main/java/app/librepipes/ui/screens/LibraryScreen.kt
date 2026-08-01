package app.librepipes.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.DownloadState
import app.librepipes.data.model.DownloadMode
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.EmptyState
import app.librepipes.ui.components.VideoRow
import app.librepipes.ui.viewmodels.LibraryViewModel
import coil3.compose.AsyncImage
import java.util.Locale

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
    onOpenLocalPlaylist: (Long) -> Unit,
    onPlayUri: (Uri, String) -> Unit,
) {
    // These VM fields are backed by mutableStateOf — Compose tracks them directly.
    val history = vm.history
    val playlists = vm.playlists
    val downloads = vm.downloads

    var selectedTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("History") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Playlists") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Downloads") })
        }

        when (selectedTab) {
            0 -> HistoryTab(history, onOpenVideo, vm::removeHistory, vm::clearHistory)
            1 -> PlaylistsTab(playlists, onOpenLocalPlaylist, vm::createPlaylist)
            2 -> DownloadsTab(downloads, onPlayUri, vm)
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<LibraryViewModel.HistoryEntry>,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }
        if (history.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.History,
                title = "No watch history",
                subtitle = "Videos you watch will appear here.",
            )
        } else {
            LazyColumn {
                items(history, key = { it.entity.streamId }) { entry ->
                    val ref = entry.ref
                    if (ref != null) {
                        val progress = if (entry.entity.durationMs > 0) {
                            (entry.entity.positionMs.toFloat() / entry.entity.durationMs).coerceIn(0f, 1f)
                        } else null
                        VideoRow(
                            ref = ref,
                            progress = progress,
                            onClick = { onOpenVideo(ref, emptyList()) },
                            trailing = {
                                IconButton(onClick = { onRemove(ref.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove from history")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<app.librepipes.data.db.LocalPlaylistEntity>,
    onOpen: (Long) -> Unit,
    onCreate: (String) -> Unit,
) {
    var showCreate by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var newName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.PlaylistPlay,
                title = "No playlists",
                subtitle = "Create a playlist and add videos to it.",
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Column(
                        modifier = Modifier
                            .width(160.dp)
                            .clickable { onOpen(playlist.id) },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, modifier = Modifier.size(40.dp))
                        }
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        androidx.compose.material3.FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Rounded.UploadFile, contentDescription = "New playlist")
        }
    }

    if (showCreate) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onCreate(newName.trim())
                        newName = ""
                        showCreate = false
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DownloadsTab(
    downloads: List<LibraryViewModel.DownloadEntry>,
    onPlayUri: (Uri, String) -> Unit,
    vm: LibraryViewModel,
) {
    if (downloads.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Download,
            title = "No downloads",
            subtitle = "Downloaded videos will appear here.",
        )
    } else {
        LazyColumn {
            items(downloads, key = { it.entity.id }) { entry ->
                val ref = entry.ref
                val entity = entry.entity
                val state = runCatching { DownloadState.valueOf(entity.state) }.getOrDefault(DownloadState.ERROR)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = ref?.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(90.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ref?.title ?: "Unknown video",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${entity.mode} • ${stateText(state, entity.progress)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when (state) {
                            DownloadState.QUEUED, DownloadState.RUNNING -> {
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { entity.progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                )
                            }
                            DownloadState.ERROR -> {
                                if (!entity.error.isNullOrBlank()) {
                                    Text(
                                        text = entity.error.take(80),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                    if (state == DownloadState.DONE && entity.fileUri != null) {
                        IconButton(onClick = { onPlayUri(Uri.parse(entity.fileUri), ref?.title ?: "") }) {
                            Icon(Icons.Rounded.PlaylistPlay, contentDescription = "Play")
                        }
                    } else if (state == DownloadState.ERROR) {
                        IconButton(onClick = { vm.retryDownload(entity.id) }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Retry")
                        }
                    } else {
                        IconButton(onClick = { vm.cancelDownload(entity.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Cancel")
                        }
                    }
                    IconButton(onClick = { vm.deleteDownload(entity.id) }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

private fun stateText(state: DownloadState, progress: Int): String = when (state) {
    DownloadState.QUEUED -> "Queued"
    DownloadState.RUNNING -> "$progress%"
    DownloadState.DONE -> "Done"
    DownloadState.ERROR -> "Failed"
    DownloadState.CANCELLED -> "Cancelled"
}


