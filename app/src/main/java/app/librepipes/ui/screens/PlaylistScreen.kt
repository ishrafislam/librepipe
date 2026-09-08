package app.librepipes.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.VideoMenuHost
import app.librepipes.ui.components.rememberVideoMenuController
import app.librepipes.ui.components.kit.LpErrorState
import app.librepipes.ui.components.kit.LpFilledButton
import app.librepipes.ui.components.kit.LpIconAction
import app.librepipes.ui.components.kit.LpListSkeleton
import app.librepipes.ui.components.kit.LpTextButton
import app.librepipes.ui.components.kit.LpTopBar
import app.librepipes.ui.components.kit.LpVideoRow
import app.librepipes.ui.components.kit.rememberDelayedSkeleton
import app.librepipes.ui.viewmodels.PlaylistViewModel
import app.librepipes.util.Format
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun PlaylistScreen(
    vm: PlaylistViewModel,
    onBack: () -> Unit,
    onPlayQueue: (selected: StreamRef, queue: List<StreamRef>) -> Unit,
) {
    val playlist = vm.playlist
    val videoMenu = rememberVideoMenuController()
    VideoMenuHost(videoMenu)
    val videos = vm.videos
    val loading = vm.loading
    val loadingMore = vm.loadingMore
    val error = vm.error

    val listState = rememberLazyListState()

    LaunchedEffect(listState, videos.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                if (videos.isNotEmpty() && last >= videos.size - 4) vm.loadMore()
            }
    }

    val skeleton = rememberDelayedSkeleton(loading && playlist == null)
    when {
        loading && playlist == null -> if (skeleton) LpListSkeleton() else Box(Modifier.fillMaxSize())
        error != null && playlist == null -> LpErrorState(
            message = error.message,
            code = error.code,
            onRetry = { vm.load() },
        )
        else -> {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (playlist != null) {
                    item(key = "header") {
                        val p = playlist
                        Column(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = p.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                                contentScale = ContentScale.Crop,
                            )
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = p.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = buildString {
                                            append(p.uploaderName.orEmpty())
                                            if (p.streamCount > 0) {
                                                if (isNotEmpty()) append(" • ")
                                                append("${Format.count(p.streamCount)} videos")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                LpFilledButton(
                                    text = if (vm.preparingQueue) "Preparing…" else "Play all",
                                    onClick = { vm.playAll(onPlayQueue) },
                                    enabled = videos.isNotEmpty() && !vm.preparingQueue,
                                    leadingIcon = Icons.Rounded.PlayArrow,
                                )
                            }
                            val queuePreparationError = vm.queuePreparationError
                            if (queuePreparationError != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = queuePreparationError.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    LpTextButton(
                                        text = "Retry",
                                        onClick = { vm.retryPlay(onPlayQueue) },
                                    )
                                }
                            }
                        }
                    }
                }
                items(videos, key = { it.id }) { video ->
                    LpVideoRow(
                        ref = video,
                        onClick = { vm.playFrom(video, onPlayQueue) },
                        onMenuClick = { videoMenu.open(video) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                if (loadingMore) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}
