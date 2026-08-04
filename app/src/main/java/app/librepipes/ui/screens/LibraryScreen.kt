package app.librepipes.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.db.LocalPlaylistEntity
import app.librepipes.ui.components.kit.LpDialog
import app.librepipes.ui.components.kit.LpEmptyState
import app.librepipes.ui.components.kit.LpIconAction
import app.librepipes.ui.components.kit.LpOutlinedTextField
import app.librepipes.ui.components.kit.LpSheet
import app.librepipes.ui.components.kit.LpTopBar
import app.librepipes.ui.theme.ShapeTokens
import app.librepipes.ui.viewmodels.LibraryViewModel

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenLocalPlaylist: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    val playlists = vm.playlists
    val counts = vm.itemCounts
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<LocalPlaylistEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        LpTopBar(
            title = "Library",
            actions = listOf(
                LpIconAction(Icons.Rounded.History, "History", onClick = onOpenHistory),
                LpIconAction(Icons.Rounded.Download, "Downloads", onClick = onOpenDownloads),
            ),
        )

        Box(modifier = Modifier.weight(1f)) {
            if (playlists.isEmpty()) {
                LpEmptyState(
                    icon = Icons.Rounded.PlaylistPlay,
                    title = "No playlists",
                    message = "Create a playlist and add videos to it.\nWatch history and downloads live in their own tabs.",
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            count = counts[playlist.id] ?: 0,
                            onClick = { onOpenLocalPlaylist(playlist.id) },
                            onLongClick = { playlistToDelete = playlist },
                        )
                    }
                }
            }

            androidx.compose.material3.FloatingActionButton(
                onClick = { showCreate = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New playlist")
            }
        }
    }

    if (showCreate) {
        LpSheet(
            title = "New playlist",
            onDismiss = { showCreate = false },
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                LpOutlinedTextField(
                    value = newName,
                    onValueChange = {
                        if (it.length <= 60) newName = it
                    },
                    label = "Name",
                    placeholder = "My playlist",
                    helperText = "${newName.length}/60",
                )
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        vm.createPlaylist(newName.trim())
                        newName = ""
                        showCreate = false
                    },
                ) {
                    Text("Create")
                }
            }
        }
    }

    val deleting = playlistToDelete
    if (deleting != null) {
        LpDialog(
            title = "Delete playlist?",
            text = "\"${deleting.name}\" and its videos will be removed from Librepipe.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                vm.deletePlaylist(deleting.id)
                playlistToDelete = null
            },
            onDismiss = { playlistToDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCard(
    playlist: LocalPlaylistEntity,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .width(160.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(ShapeTokens.md)
                .background(colors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlaylistPlay,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = if (count == 1) "1 video" else "$count videos",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}
