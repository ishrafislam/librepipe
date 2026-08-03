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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.ErrorState
import app.librepipes.ui.components.LoadingState
import app.librepipes.ui.components.kit.LpIconAction
import app.librepipes.ui.components.kit.LpPillButton
import app.librepipes.ui.components.kit.LpTopBar
import app.librepipes.ui.components.kit.LpVideoRow
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
    val channel = vm.channel
    val videos = vm.videos
    val loading = vm.loading
    val loadingMore = vm.loadingMore
    val error = vm.error
    val subscribed = vm.subscribed
    var tab by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()

    LaunchedEffect(listState, videos.size, tab) {
        if (tab != 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                if (videos.isNotEmpty() && last >= videos.size - 4) vm.loadMore()
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LpTopBar(
            title = channel?.name ?: "Channel",
            onNavigationClick = onBack,
            navigationIcon = Icons.Rounded.ArrowBack,
            actions = listOf(
                LpIconAction(Icons.Rounded.MoreVert, null) {},
            ),
        )

        when {
            loading && channel == null -> LoadingState()
            error != null && channel == null -> ErrorState(error.orEmpty(), onRetry = { vm.load() })
            else -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (channel != null) {
                        item(key = "header") {
                            ChannelHeader(
                                channel = channel!!,
                                subscribed = subscribed,
                                onToggleSubscribe = vm::toggleSubscribe,
                            )
                        }
                        item(key = "tabs") {
                            TabRow(
                                selectedTabIndex = tab,
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                indicator = { tabPositions ->
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[tab]),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                            ) {
                                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Videos") })
                                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("About") })
                            }
                        }
                    }
                    if (tab == 0) {
                        items(videos, key = { it.id }) { video ->
                            LpVideoRow(
                                ref = video,
                                onClick = { onOpenVideo(video, videos) },
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
                    } else {
                        item(key = "about") {
                            channel?.let { AboutTab(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    channel: ChannelRef,
    subscribed: Boolean,
    onToggleSubscribe: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!channel.bannerUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.bannerUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(21f / 9f),
                contentScale = ContentScale.Crop,
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = channel.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (channel.subscriberCount > 0) {
                        Text(
                            text = "${Format.count(channel.subscriberCount)} subscribers",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                LpPillButton(
                    text = if (subscribed) "Subscribed" else "Subscribe",
                    onClick = onToggleSubscribe,
                )
            }
        }
    }
}

@Composable
private fun AboutTab(channel: ChannelRef) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        if (!channel.description.isNullOrBlank()) {
            Text(
                text = channel.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
        } else {
            Text(
                text = "No description provided by this channel.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (channel.subscriberCount > 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "${Format.count(channel.subscriberCount)} subscribers",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
