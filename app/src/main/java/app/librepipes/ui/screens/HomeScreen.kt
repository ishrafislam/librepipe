package app.librepipes.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.R
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.kit.LpEmptyState
import app.librepipes.ui.components.kit.LpErrorState
import app.librepipes.ui.components.kit.LpFeedSkeleton
import app.librepipes.ui.components.kit.LpFilterChip
import app.librepipes.ui.components.kit.LpVideoCard
import app.librepipes.ui.components.kit.rememberDelayedSkeleton
import app.librepipes.ui.theme.ShapeTokens
import app.librepipes.ui.viewmodels.HomeViewModel
import app.librepipes.util.Connectivity

private enum class HomeFilter(val label: String) {
    ALL("All"),
    CONTINUE("Continue"),
    LIVE("Live"),
    DOWNLOADED("Downloaded"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val state = vm.uiState.collectAsState().value
    var filter by remember { mutableStateOf(HomeFilter.ALL) }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val online by Connectivity.observeOnline(context).collectAsState(initial = Connectivity.isOnline(context))

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(onOpenSearch = onOpenSearch)

            val showError = state.error != null && state.sections.isEmpty() && state.trending.isEmpty()
            val skeleton = rememberDelayedSkeleton(
                state.loading && state.sections.isEmpty() && state.trending.isEmpty(),
            )
            when {
                showError -> if (!online) {
                    LpErrorState(
                        title = "You're offline",
                        message = state.error?.message.orEmpty(),
                        icon = Icons.Rounded.CloudOff,
                        code = "OFFLINE",
                        onRetry = { vm.refresh() },
                    )
                } else {
                    LpErrorState(
                        message = state.error?.message.orEmpty(),
                        code = state.error?.code,
                        onRetry = { vm.refresh() },
                    )
                }

                state.loading && state.sections.isEmpty() && state.trending.isEmpty() ->
                    if (skeleton) LpFeedSkeleton() else Box(Modifier.fillMaxSize())

                !state.hasSubscriptions -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (state.trending.isEmpty()) {
                            item {
                                LpEmptyState(
                                    icon = Icons.Rounded.Subscriptions,
                                    title = "Nothing here yet",
                                    message = "Subscribe to channels to see their latest uploads here.\nIn the meantime, enjoy what's trending.",
                                )
                            }
                        } else {
                            item {
                                TrendingHeader()
                            }
                            items(state.trending, key = { it.id }) { video ->
                                TrendingCard(
                                    ref = video,
                                    onClick = { onOpenVideo(video, state.trending) },
                                )
                            }
                        }
                    }
                }

                else -> {
                    val visibleSections = state.sections.mapNotNull { section ->
                        val videos = when (filter) {
                            HomeFilter.ALL -> section.videos
                            HomeFilter.CONTINUE -> section.videos.filter { v -> state.inProgress.any { it.id == v.id } }
                            HomeFilter.LIVE -> section.videos.filter { it.isLive }
                            HomeFilter.DOWNLOADED -> section.videos.filter { v -> v.id in state.downloadedIds }
                        }
                        if (videos.isEmpty()) null else section.copy(videos = videos)
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            FilterChips(
                                selected = filter,
                                counts = homeFilterCounts(state),
                                onSelect = { filter = it },
                            )
                        }
                        if (visibleSections.isEmpty() && !state.loading) {
                            item {
                                LpEmptyState(
                                    icon = Icons.Rounded.Search,
                                    title = "No uploads here",
                                    message = when (filter) {
                                        HomeFilter.CONTINUE -> "Videos you're in the middle of will show up here."
                                        HomeFilter.LIVE -> "Live streams from your subscriptions will show up here."
                                        HomeFilter.DOWNLOADED -> "Downloaded videos will show up here."
                                        HomeFilter.ALL -> "Pull down to refresh the feed."
                                    },
                                )
                            }
                        }
                        items(visibleSections, key = { it.channel.id }) { section ->
                            SectionHeader(
                                title = section.channel.name,
                                onMore = { onOpenChannel(section.channel.url) },
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                            ) {
                                items(section.videos, key = { it.id }) { video ->
                                    LpVideoCard(
                                        ref = video,
                                        onClick = { onOpenVideo(video, section.videos) },
                                        showChannel = false,
                                        progress = state.progressById[video.id],
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun homeFilterCounts(state: HomeViewModel.UiState): Map<HomeFilter, Int> {
    val all = state.sections.sumOf { it.videos.size }
    val live = state.sections.sumOf { s -> s.videos.count { it.isLive } }
    val downloaded = state.sections.sumOf { s -> s.videos.count { v -> v.id in state.downloadedIds } }
    val continueCount = state.sections.sumOf { s -> s.videos.count { v -> state.inProgress.any { it.id == v.id } } }
    return mapOf(
        HomeFilter.ALL to all,
        HomeFilter.CONTINUE to continueCount,
        HomeFilter.LIVE to live,
        HomeFilter.DOWNLOADED to downloaded,
    )
}

@Composable
private fun HomeTopBar(onOpenSearch: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .height(64.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(ShapeTokens.full)
                .background(colors.surfaceContainer)
                .clickable(onClick = onOpenSearch),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Search Librepipe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .padding(12.dp),
        )
    }
}

@Composable
private fun FilterChips(
    selected: HomeFilter,
    counts: Map<HomeFilter, Int>,
    onSelect: (HomeFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(HomeFilter.entries.toList()) { filter ->
            LpFilterChip(
                text = "${filter.label} ${counts[filter] ?: 0}".trim(),
                selected = selected == filter,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, onMore: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .padding(13.dp)
                .clickable(onClick = onMore),
        )
    }
}

@Composable
private fun TrendingHeader() {
    SectionHeader(title = "Trending", onMore = {})
}

@Composable
private fun TrendingCard(ref: StreamRef, onClick: () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        LpVideoCard(ref = ref, onClick = onClick, width = 320.dp)
    }
}
