package app.librepipes.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.DownloadMode
import app.librepipes.data.model.DownloadState
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.kit.LpDialog
import app.librepipes.ui.components.kit.LpEmptyState
import app.librepipes.ui.components.kit.LpIconAction
import app.librepipes.ui.components.kit.LpLinearProgress
import app.librepipes.ui.components.kit.LpTopBar
import app.librepipes.ui.theme.PlexMono
import app.librepipes.ui.theme.ShapeTokens
import app.librepipes.ui.viewmodels.DownloadsViewModel
import coil3.compose.AsyncImage

@Composable
fun DownloadsScreen(
    vm: DownloadsViewModel,
    onBack: () -> Unit,
    onPlayUri: (Uri, String) -> Unit,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
) {
    val downloads = vm.downloads
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LpTopBar(
            title = "Downloads",
            onNavigationClick = onBack,
            navigationIcon = Icons.Rounded.ArrowBack,
            actions = listOf(
                LpIconAction(
                    icon = Icons.Rounded.DeleteSweep,
                    contentDescription = "Delete all downloads",
                    onClick = { confirmClear = true },
                ),
            ),
        )

        if (downloads.isEmpty()) {
            LpEmptyState(
                icon = Icons.Rounded.Download,
                title = "No downloads",
                message = "Downloaded videos and audio will appear here.",
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(downloads, key = { it.entity.id }) { entry ->
                val ref = entry.ref
                val entity = entry.entity
                val state = runCatching { DownloadState.valueOf(entity.state) }
                    .getOrDefault(DownloadState.ERROR)

                DownloadRow(
                    vm = vm,
                    id = entity.id,
                    ref = ref,
                    state = state,
                    mode = runCatching { DownloadMode.valueOf(entity.mode) }.getOrDefault(DownloadMode.VIDEO),
                    progress = entity.progress,
                    error = entity.error,
                    fileUri = entity.fileUri,
                    onPlay = { onPlayUri(Uri.parse(entity.fileUri!!), ref?.title ?: "") },
                )
            }
        }
    }

    if (confirmClear) {
        LpDialog(
            title = "Delete all downloads?",
            text = "Every downloaded file will be removed from this device.",
            confirmLabel = "Delete all",
            destructive = true,
            onConfirm = {
                vm.clear()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun DownloadRow(
    vm: DownloadsViewModel,
    id: Long,
    ref: StreamRef?,
    state: DownloadState,
    mode: DownloadMode,
    progress: Int,
    error: String?,
    fileUri: String?,
    onPlay: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
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
                .width(96.dp)
                .height(54.dp)
                .clip(ShapeTokens.md),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ref?.title ?: "Unknown video",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusLine(state, mode, progress),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PlexMono,
                color = if (state == DownloadState.ERROR) colors.error else colors.onSurfaceVariant,
                maxLines = 1,
            )
            when (state) {
                DownloadState.QUEUED, DownloadState.RUNNING -> {
                    Spacer(Modifier.height(6.dp))
                    LpLinearProgress(progress = progress / 100f)
                }
                DownloadState.ERROR -> {
                    if (!error.isNullOrBlank()) {
                        Text(
                            text = error.take(60),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                else -> {}
            }
        }
        Spacer(Modifier.width(8.dp))
        when (state) {
            DownloadState.DONE -> if (fileUri != null) {
                RoundAction(icon = Icons.Rounded.PlayArrow, contentDescription = "Play", onClick = onPlay)
            } else {
                RoundAction(icon = Icons.Rounded.Delete, contentDescription = "Remove", onClick = { vm.delete(id) })
            }
            DownloadState.ERROR -> RoundAction(icon = Icons.Rounded.Refresh, contentDescription = "Retry", onClick = { vm.retry(id) })
            DownloadState.CANCELLED -> RoundAction(icon = Icons.Rounded.Delete, contentDescription = "Remove", onClick = { vm.delete(id) })
            DownloadState.QUEUED, DownloadState.RUNNING -> RoundAction(icon = Icons.Rounded.Delete, contentDescription = "Cancel", onClick = { vm.cancel(id) })
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun statusLine(state: DownloadState, mode: DownloadMode, progress: Int): String = when (state) {
    DownloadState.QUEUED -> "${mode.name.titleCase()} • queued"
    DownloadState.RUNNING -> "${mode.name.titleCase()} • $progress%"
    DownloadState.DONE -> "${mode.name.titleCase()} • done"
    DownloadState.ERROR -> "${mode.name.titleCase()} • failed"
    DownloadState.CANCELLED -> "${mode.name.titleCase()} • cancelled"
}

private fun String.titleCase(): String = lowercase().replaceFirstChar { it.uppercase() }
