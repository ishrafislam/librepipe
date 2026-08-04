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
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import app.librepipes.ui.theme.Spacing
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
    onOpenSearch: () -> Unit,
) {
    val state = vm.uiState.collectAsState().value
    var filter by rememberSaveable { mutableStateOf(HomeFilter.ALL) }
    val context = LocalContext.current
    val online by Connectivity.observeOnline(context).collectAsState(initial = Connectivity.isOnline(context))

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(onOpenSearch = onOpenSearch)

            val showError = state.error != null && state.feed.isEmpty()
            val skeleton = rememberDelayedSkeleton(state.loading && state.feed.isEmpty())
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

                state.loading && state.feed.isEmpty() ->
                    if (skeleton) LpFeedSkeleton() else Box(Modifier.fillMaxSize())

                else -> {
                    val visible = remember(state.feed, filter, state.inProgressIds, state.downloadedIds) {
                        when (filter) {
                            HomeFilter.ALL -> state.feed
                            HomeFilter.CONTINUE -> state.feed.filter { it.id in state.inProgressIds }
                            HomeFilter.LIVE -> state.feed.filter { it.isLive }
                            HomeFilter.DOWNLOADED -> state.feed.filter { it.id in state.downloadedIds }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Spacing.space4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space5),
                    ) {
                        item {
                            FilterChips(selected = filter, onSelect = { filter = it })
                        }
                        if (visible.isEmpty() && !state.loading) {
                            item {
                                LpEmptyState(
                                    icon = Icons.Rounded.Search,
                                    title = "Nothing here",
                                    message = when (filter) {
                                        HomeFilter.CONTINUE -> "Videos you're in the middle of will show up here."
                                        HomeFilter.LIVE -> "Live streams will show up here."
                                        HomeFilter.DOWNLOADED -> "Finished downloads will show up here."
                                        HomeFilter.ALL -> "Pull down to refresh the feed."
                                    },
                                )
                            }
                        }
                        items(visible, key = { it.id }) { video ->
                            LpVideoCard(
                                ref = video,
                                onClick = { onOpenVideo(video, visible) },
                                width = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.space4),
                                progress = state.progressById[video.id],
                            )
                        }
                    }
                }
            }
        }
    }
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
        Text(
            text = "Librepipe",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.Search,
            contentDescription = "Search",
            tint = colors.onSurface,
            modifier = Modifier
                .size(48.dp)
                .clip(ShapeTokens.full)
                .clickable(onClick = onOpenSearch)
                .padding(12.dp),
        )
    }
}

@Composable
private fun FilterChips(
    selected: HomeFilter,
    onSelect: (HomeFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(HomeFilter.entries.toList()) { filter ->
            LpFilterChip(
                text = filter.label,
                selected = selected == filter,
                onClick = { onSelect(filter) },
            )
        }
    }
}

