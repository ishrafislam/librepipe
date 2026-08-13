package app.librepipes.ui.screens

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.kit.LpEmptyState
import app.librepipes.ui.components.kit.LpErrorState
import app.librepipes.ui.components.kit.LpFilledButton
import app.librepipes.ui.components.kit.LpIconButton
import app.librepipes.ui.components.kit.LpListSkeleton
import app.librepipes.ui.components.kit.LpOutlinedButton
import app.librepipes.ui.components.kit.LpPlaylistRow
import app.librepipes.ui.components.kit.LpVideoRow
import app.librepipes.ui.components.kit.rememberDelayedSkeleton
import app.librepipes.ui.viewmodels.ChannelViewModel
import app.librepipes.util.Format
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged

private const val TAB_VIDEOS = 0
private const val TAB_PLAYLISTS = 1
private const val TAB_ABOUT = 2

@Composable
fun ChannelScreen(
    vm: ChannelViewModel,
    onBack: () -> Unit,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val channel = vm.channel
    val videos = vm.videos
    val loading = vm.loading
    val loadingMore = vm.loadingMore
    val error = vm.error
    val subscribed = vm.subscribed
    var tab by remember { mutableIntStateOf(TAB_VIDEOS) }

    val listState = rememberLazyListState()

    LaunchedEffect(listState, videos.size, tab) {
        if (tab != TAB_VIDEOS) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last ->
                if (videos.isNotEmpty() && last >= videos.size - 4) vm.loadMore()
            }
    }

    LaunchedEffect(tab) {
        if (tab == TAB_PLAYLISTS) vm.loadPlaylists()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val skeleton = rememberDelayedSkeleton(loading && channel == null)
        when {
            loading && channel == null -> if (skeleton) LpListSkeleton() else Box(Modifier.fillMaxSize())
            error != null && channel == null -> LpErrorState(
                message = error?.message.orEmpty(),
                code = error?.code,
                onRetry = { vm.load() },
            )
            else -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (channel != null) {
                        item(key = "header") {
                            ChannelHeader(
                                channel = channel!!,
                                subscribed = subscribed,
                                onToggleSubscribe = vm::toggleSubscribe,
                                onBack = onBack,
                                onOpenSearch = onOpenSearch,
                            )
                        }
                        item(key = "tabs") {
                            TabRow(
                                selectedTabIndex = tabPosition(tab, vm.hasPlaylists),
                                containerColor = Color.Transparent,
                                indicator = { tabPositions ->
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(
                                            tabPositions[tabPosition(tab, vm.hasPlaylists)],
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                            ) {
                                Tab(
                                    selected = tab == TAB_VIDEOS,
                                    onClick = { tab = TAB_VIDEOS },
                                    text = { Text("Videos") },
                                )
                                if (vm.hasPlaylists) {
                                    Tab(
                                        selected = tab == TAB_PLAYLISTS,
                                        onClick = { tab = TAB_PLAYLISTS },
                                        text = { Text("Playlists") },
                                    )
                                }
                                Tab(
                                    selected = tab == TAB_ABOUT,
                                    onClick = { tab = TAB_ABOUT },
                                    text = { Text("About") },
                                )
                            }
                        }
                    }
                    when (tab) {
                        TAB_VIDEOS -> {
                            items(videos, key = { it.id }) { video ->
                                LpVideoRow(
                                    ref = video,
                                    onClick = { onOpenVideo(video, videos) },
                                    showMenu = false,
                                    showChannel = false,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                            if (loadingMore) item { RowSpinner() }
                        }

                        TAB_PLAYLISTS -> {
                            if (vm.playlistsLoading) {
                                item { RowSpinner() }
                            } else if (vm.playlists.isEmpty()) {
                                item {
                                    LpEmptyState(
                                        icon = Icons.Rounded.Search,
                                        title = "No playlists",
                                        message = "This channel has no public playlists.",
                                    )
                                }
                            } else {
                                items(vm.playlists, key = { it.id }) { playlist ->
                                    LpPlaylistRow(
                                        playlist = playlist,
                                        onClick = { onOpenPlaylist(playlist.url) },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        else -> item(key = "about") { channel?.let { AboutTab(it) } }
                    }
                }
            }
        }
    }
}

/** About shifts down a slot when the channel has no playlists tab. */
private fun tabPosition(tab: Int, hasPlaylists: Boolean): Int =
    if (hasPlaylists || tab != TAB_ABOUT) tab else TAB_PLAYLISTS

@Composable
private fun RowSpinner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun ChannelHeader(
    channel: ChannelRef,
    subscribed: Boolean,
    onToggleSubscribe: () -> Unit,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var descriptionExpanded by remember { mutableStateOf(false) }
    var descriptionOverflows by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = channel.bannerUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 5f)
                    .background(colors.surfaceContainerHigh),
                contentScale = ContentScale.Crop,
            )
            // Scrim: the bar's icons sit on whatever artwork the channel uploaded.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LpIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                LpIconButton(
                    icon = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = Color.White,
                    onClick = onOpenSearch,
                )
            }
            // Inside the banner Box and overflowing it, so the overlap costs no layout
            // height — an offset on a sibling would leave a phantom gap below.
            AsyncImage(
                model = channel.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 16.dp, y = 44.dp)
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh),
                contentScale = ContentScale.Crop,
            )
        }

        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 52.dp)) {
            Column {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                channelStats(channel)?.let { stats ->
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (!channel.description.isNullOrBlank()) {
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        // Only the collapsed pass can overflow; letting the expanded one
                        // write here would clear the flag and strip the "less" link away.
                        onTextLayout = { result ->
                            if (!descriptionExpanded) descriptionOverflows = result.hasVisualOverflow
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    if (descriptionOverflows) {
                        Text(
                            text = if (descriptionExpanded) "less" else "more",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { descriptionExpanded = !descriptionExpanded },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (subscribed) {
                    LpOutlinedButton(text = "Subscribed", onClick = onToggleSubscribe)
                } else {
                    LpFilledButton(text = "Subscribe", onClick = onToggleSubscribe)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** "21.1M subscribers · 486 videos", dropping whichever half is missing. */
private fun channelStats(channel: ChannelRef): String? = listOfNotNull(
    channel.subscriberCount.takeIf { it > 0 }?.let { "${Format.count(it)} subscribers" },
    channel.videoCount.takeIf { it > 0 }?.let { "${Format.count(it)} videos" },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

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
        channelStats(channel)?.let { stats ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = stats,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
