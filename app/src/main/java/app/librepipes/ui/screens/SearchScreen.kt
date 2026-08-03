package app.librepipes.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.EmptyState
import app.librepipes.ui.components.ErrorState
import app.librepipes.ui.components.kit.LpChannelRow
import app.librepipes.ui.components.kit.LpFilterChip
import app.librepipes.ui.components.kit.LpPlaylistRow
import app.librepipes.ui.components.kit.LpSearchBar
import app.librepipes.ui.components.kit.LpSheet
import app.librepipes.ui.components.kit.LpVideoRow
import app.librepipes.ui.theme.PlexMono
import app.librepipes.ui.viewmodels.SearchViewModel
import app.librepipes.util.Format
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onBack: () -> Unit,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val query = vm.query
    val suggestions = vm.suggestions
    val items = vm.items
    val loading = vm.loading
    val error = vm.error
    val hasMore = vm.hasMore
    val activeFilter = vm.activeFilter
    val searched = vm.searched
    val recents = vm.recents

    val listState = rememberLazyListState()
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(listState, items.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }
            .distinctUntilChanged()
            .collect { last ->
                if (items.isNotEmpty() && last >= items.size - 4 && hasMore) vm.loadMore()
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LpSearchBar(
            value = query,
            onValueChange = vm::onQueryChange,
            onBack = onBack,
            placeholder = "Search YouTube or YouTube Music",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )

        when {
            query.isBlank() -> RecentsSection(vm, recents)

            else -> {
                FilterChipsRow(vm, activeFilter, onShowFilters = { showFilters = true })

                if (!searched && suggestions.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(suggestions) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.onQueryChange(suggestion)
                                        vm.search()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else {
                    ResultsSection(
                        vm = vm,
                        items = items,
                        loading = loading,
                        error = error,
                        searched = searched,
                        hasMore = hasMore,
                        activeFilter = activeFilter,
                        listState = listState,
                        onOpenVideo = onOpenVideo,
                        onOpenChannel = onOpenChannel,
                        onOpenPlaylist = onOpenPlaylist,
                    )
                }
            }
        }
    }

    if (showFilters) {
        LpSheet(
            title = "Search filters",
            onDismiss = { showFilters = false },
        ) {
            Extractor.SearchFilter.entries.forEach { filter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            vm.search(filter)
                            showFilters = false
                        }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (activeFilter == filter) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentsSection(vm: SearchViewModel, recents: List<app.librepipes.data.db.SearchHistoryEntity>) {
    val colors = MaterialTheme.colorScheme
    if (recents.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.Search,
            title = "Search Librepipe",
            subtitle = "Find videos, channels and playlists across YouTube and YouTube Music.",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent searches",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::clearRecents) {
                    Text("Clear")
                }
            }
        }
        items(recents, key = { it.id }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        vm.onQueryChange(entry.query)
                        vm.search()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = entry.query,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp)
                        .clickable { vm.removeRecent(entry.id) },
                )
            }
        }
        item {
            Text(
                text = "Searches are stored only on this device.",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = PlexMono,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun FilterChipsRow(
    vm: SearchViewModel,
    activeFilter: Extractor.SearchFilter,
    onShowFilters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(Extractor.SearchFilter.entries.toList()) { filter ->
                LpFilterChip(
                    text = filter.label,
                    selected = activeFilter == filter,
                    onClick = { vm.search(filter) },
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Rounded.Tune,
            contentDescription = "More filters",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .padding(12.dp)
                .clickable(onClick = onShowFilters),
        )
    }
}

@Composable
private fun ResultsSection(
    vm: SearchViewModel,
    items: List<Extractor.SearchItem>,
    loading: Boolean,
    error: String?,
    searched: Boolean,
    hasMore: Boolean,
    activeFilter: Extractor.SearchFilter,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    when {
            error != null && items.isEmpty() -> ErrorState(error, onRetry = { vm.search() })

            loading && items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            searched && items.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Search,
                title = "No results",
                subtitle = "Try different keywords or another filter.",
            )

            else -> {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.key() }) { item ->
                        when (item) {
                            is Extractor.SearchItem.Video -> LpVideoRow(
                                ref = item.stream,
                                onClick = { onOpenVideo(item.stream, emptyList()) },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            is Extractor.SearchItem.Channel -> LpChannelRow(
                                channel = item.channel,
                                onClick = { onOpenChannel(item.channel.url) },
                                subtitle = item.channel.subscriberCount
                                    .let { if (it > 0) "${Format.count(it)} subscribers" else null },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            is Extractor.SearchItem.Playlist -> LpPlaylistRow(
                                playlist = item.playlist,
                                onClick = { onOpenPlaylist(item.playlist.url) },
                                subtitle = "${item.playlist.streamCount} videos",
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                    if (hasMore) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.height(28.dp).width(28.dp))
                            }
                        }
                    }
                }
            }
        }
}

private val Extractor.SearchFilter.label: String
    get() = when (this) {
        Extractor.SearchFilter.ALL -> "All"
        Extractor.SearchFilter.VIDEOS -> "Videos"
        Extractor.SearchFilter.CHANNELS -> "Channels"
        Extractor.SearchFilter.PLAYLISTS -> "Playlists"
        Extractor.SearchFilter.MUSIC -> "Music"
    }
