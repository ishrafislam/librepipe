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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.ErrorState
import app.librepipes.ui.components.LoadingState
import app.librepipes.ui.components.VideoRow
import app.librepipes.ui.viewmodels.PlaylistViewModel
import app.librepipes.util.Format
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun PlaylistScreen(
    vm: PlaylistViewModel,
    onBack: () -> Unit,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
) {
    // These VM fields are backed by mutableStateOf — Compose tracks them directly.
    val playlist = vm.playlist
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

    when {
        loading && playlist == null -> LoadingState()
        error != null && playlist == null -> ErrorState(error.orEmpty(), onRetry = { vm.load() })
        else -> {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (playlist != null) {
                    item(key = "header") {
                        val p = playlist!!
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = p.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(0.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = androidx.compose.ui.graphics.Color.White,
                                )
                            }
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
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
                                Spacer(Modifier.height(12.dp))
                                if (videos.isNotEmpty()) {
                                    Button(
                                        onClick = { onOpenVideo(videos.first(), videos) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play all")
                                    }
                                }
                            }
                        }
                    }
                }
                items(videos, key = { it.id }) { video ->
                    VideoRow(
                        ref = video,
                        index = videos.indexOf(video),
                        onClick = { onOpenVideo(video, videos.drop(videos.indexOf(video))) },
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
