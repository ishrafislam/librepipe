package app.librepipes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.VideoMenuHost
import app.librepipes.ui.components.rememberVideoMenuController
import app.librepipes.ui.components.kit.LpEmptyState
import app.librepipes.ui.components.kit.LpErrorState
import app.librepipes.ui.components.kit.LpFeedSkeleton
import app.librepipes.ui.components.kit.LpIconAction
import app.librepipes.ui.components.kit.LpListSkeleton
import app.librepipes.ui.components.kit.LpTopBar
import app.librepipes.ui.components.kit.LpVideoCard
import app.librepipes.ui.components.kit.LpVideoRow
import app.librepipes.ui.theme.Spacing
import app.librepipes.ui.viewmodels.SubscriptionsViewModel
import app.librepipes.ui.viewmodels.SubscriptionsViewModel.ViewMode
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SubscriptionsScreen(
    vm: SubscriptionsViewModel,
    onOpenVideo: (StreamRef) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val grid = vm.viewMode == ViewMode.GRID
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val selectedChannel = vm.channels
        .firstOrNull { it.subscription.channelUrl == vm.selectedChannelUrl }
        ?.subscription

    // Scoping to a channel swaps the whole list under a retained scroll state; without
    // this the pagination trigger can fire straight away on a shorter channel.
    LaunchedEffect(vm.selectedChannelUrl) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }

    BackHandler(enabled = vm.selectedChannelUrl != null) {
        vm.selectChannel(null)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LpTopBar(
            title = selectedChannel?.name ?: "Subscriptions",
            navigationIcon = selectedChannel?.let { Icons.AutoMirrored.Rounded.ArrowBack },
            onNavigationClick = selectedChannel?.let { { vm.selectChannel(null) } },
            actions = listOf(
                LpIconAction(Icons.Rounded.Search, "Search", onOpenSearch),
                LpIconAction(
                    icon = if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = if (grid) "List view" else "Grid view",
                    onClick = { vm.setViewMode(if (grid) ViewMode.LIST else ViewMode.GRID) },
                ),
            ),
        )

        if (vm.channels.isEmpty()) {
            LpEmptyState(
                icon = Icons.Rounded.Subscriptions,
                title = "No subscriptions",
                message = "Channels you subscribe to will show up here.",
            )
            return@Column
        }

        ChannelStrip(
            channels = vm.channels,
            selectedUrl = vm.selectedChannelUrl,
            onSelect = vm::selectChannel,
            onOpenChannel = onOpenChannel,
        )

        when {
            vm.error != null && vm.videos.isEmpty() -> LpErrorState(
                message = vm.error?.message.orEmpty(),
                code = vm.error?.code,
                onRetry = vm::refresh,
            )

            vm.loading && vm.videos.isEmpty() ->
                if (grid) LpFeedSkeleton() else LpListSkeleton()

            vm.videos.isEmpty() -> LpEmptyState(
                icon = Icons.Rounded.Subscriptions,
                title = "No uploads yet",
                message = "Nothing new from these channels.",
            )

            grid -> VideoGrid(vm, gridState, onOpenVideo)
            else -> VideoList(vm, listState, onOpenVideo)
        }
    }
}

/**
 * Horizontal avatar strip. A ring marks channels with new uploads; tapping filters the
 * feed to that channel, tapping it again clears back to every subscription.
 */
@Composable
private fun ChannelStrip(
    channels: List<SubscriptionsViewModel.ChannelItem>,
    selectedUrl: String?,
    onSelect: (String?) -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(channels, key = { it.subscription.channelUrl }) { item ->
            val sub = item.subscription
            val selected = sub.channelUrl == selectedUrl
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .clickable { onSelect(if (selected) null else sub.channelUrl) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .then(
                            when {
                                selected -> Modifier.border(3.dp, colors.primary, CircleShape)
                                item.hasNew -> Modifier.border(3.dp, colors.tertiary, CircleShape)
                                else -> Modifier
                            },
                        )
                        // 3dp stroke + 3dp gap, so the avatar doesn't touch the ring.
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh),
                ) {
                    AsyncImage(
                        model = sub.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = sub.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { onOpenChannel(sub.channelUrl) },
                )
            }
        }
    }
}

@Composable
private fun VideoList(
    vm: SubscriptionsViewModel,
    listState: LazyListState,
    onOpenVideo: (StreamRef) -> Unit,
) {
    val videoMenu = rememberVideoMenuController()
    VideoMenuHost(videoMenu)
    PaginateOnScroll(vm, listState.layoutInfoLastIndex())
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(vm.videos, key = { it.id }) { ref ->
            LpVideoRow(
                ref = ref,
                onClick = { onOpenVideo(ref) },
                onMenuClick = { videoMenu.open(ref) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (vm.loadingMore) item { LoadingMoreRow() }
    }
}

@Composable
private fun VideoGrid(
    vm: SubscriptionsViewModel,
    gridState: LazyGridState,
    onOpenVideo: (StreamRef) -> Unit,
) {
    val videoMenu = rememberVideoMenuController()
    VideoMenuHost(videoMenu)
    PaginateOnScroll(vm, gridState.layoutInfoLastIndex())
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        items(vm.videos, key = { it.id }) { ref ->
            LpVideoCard(
                ref = ref,
                onClick = { onOpenVideo(ref) },
                onMenuClick = { videoMenu.open(ref) },
                width = null,
                showAvatar = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (vm.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) { LoadingMoreRow() }
        }
    }
}

/** Shared infinite-scroll trigger — same shape as Search, Channel and Playlist. */
@Composable
private fun PaginateOnScroll(vm: SubscriptionsViewModel, lastVisibleIndex: () -> Int) {
    LaunchedEffect(vm, vm.videos.size) {
        snapshotFlow(lastVisibleIndex)
            .distinctUntilChanged()
            .collect { last ->
                if (vm.videos.isNotEmpty() && last >= vm.videos.size - 4 && vm.hasMore) {
                    vm.loadMore()
                }
            }
    }
}

private fun LazyListState.layoutInfoLastIndex(): () -> Int =
    { layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }

private fun LazyGridState.layoutInfoLastIndex(): () -> Int =
    { layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }

@Composable
private fun LoadingMoreRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}
