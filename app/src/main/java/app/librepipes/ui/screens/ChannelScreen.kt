package app.librepipes.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.librepipes.ui.viewmodels.ChannelViewModel
import app.librepipes.util.Format
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ChannelScreen(
    vm: ChannelViewModel,
    onBack: () -> Unit,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
) {
    // These VM fields are backed by mutableStateOf — Compose tracks them directly.
    val channel = vm.channel
    val videos = vm.videos
    val loading = vm.loading
    val loadingMore = vm.loadingMore
    val error = vm.error
    val subscribed = vm.subscribed

    val listState = rememberLazyListState()

    LaunchedEffect(listState, videos.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                if (videos.isNotEmpty() && last >= videos.size - 4) vm.loadMore()
            }
    }

    when {
        loading && channel == null -> LoadingState()
        error != null && channel == null -> ErrorState(error.orEmpty(), onRetry = { vm.load() })
        else -> {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                if (channel != null) {
                    item(key = "header") {
                        val ch = channel!!
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (!ch.bannerUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ch.bannerUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(21f / 9f),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = ch.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ch.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (ch.subscriberCount > 0) {
                                        Text(
                                            text = "${Format.count(ch.subscriberCount)} subscribers",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (subscribed) {
                                    OutlinedButton(onClick = { vm.toggleSubscribe() }) {
                                        Icon(Icons.Rounded.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Subscribed")
                                    }
                                } else {
                                    Button(onClick = { vm.toggleSubscribe() }) {
                                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Subscribe")
                                    }
                                }
                            }
                            if (!ch.description.isNullOrBlank()) {
                                Text(
                                    text = ch.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                items(videos, key = { it.id }) { video ->
                    VideoRow(
                        ref = video,
                        onClick = { onOpenVideo(video, videos) },
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
