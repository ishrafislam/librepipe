package app.librepipes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.EmptyState
import app.librepipes.ui.components.ErrorState
import app.librepipes.ui.components.LoadingState
import app.librepipes.ui.components.SectionHeader
import app.librepipes.ui.components.VideoCard
import app.librepipes.ui.components.VideoRow
import app.librepipes.ui.viewmodels.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
    onOpenChannel: (String) -> Unit,
) {
    val state = vm.uiState.collectAsState().value

    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        val showError = state.error != null && state.sections.isEmpty() && state.trending.isEmpty()
        when {
            showError -> ErrorState(state.error.orEmpty(), onRetry = { vm.refresh() })

            state.loading && state.sections.isEmpty() && state.trending.isEmpty() ->
                LoadingState()

            !state.hasSubscriptions -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (state.trending.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Subscriptions,
                                title = "Nothing here yet",
                                subtitle = "Subscribe to channels to see their latest uploads here.\nIn the meantime, enjoy what's trending.",
                            )
                        }
                    } else {
                        item {
                            SectionHeader("Trending")
                        }
                        items(state.trending, key = { it.id }) { video ->
                            VideoRow(
                                ref = video,
                                onClick = { onOpenVideo(video, state.trending) },
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (state.sections.isEmpty() && !state.loading) {
                        item {
                            EmptyState(
                                icon = Icons.Rounded.Subscriptions,
                                title = "No uploads yet",
                                subtitle = "Pull down to refresh the feed.",
                            )
                        }
                    }
                    items(state.sections, key = { it.channel.id }) { section ->
                        SectionHeader(
                            title = section.channel.name,
                            onMore = { onOpenChannel(section.channel.url) },
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                        ) {
                            items(section.videos, key = { it.id }) { video ->
                                VideoCard(
                                    ref = video,
                                    onClick = { onOpenVideo(video, section.videos) },
                                    showChannel = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
