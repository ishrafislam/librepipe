package app.librepipes.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.ChannelRow
import app.librepipes.ui.components.EmptyState
import app.librepipes.ui.components.ErrorState
import app.librepipes.ui.components.VideoRow
import app.librepipes.ui.viewmodels.SearchViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    // These VM fields are backed by mutableStateOf — Compose tracks them directly.
    val query = vm.query
    val suggestions = vm.suggestions
    val items = vm.items
    val loading = vm.loading
    val error = vm.error
    val hasMore = vm.hasMore
    val activeFilter = vm.activeFilter
    val searched = vm.searched

    val listState = rememberLazyListState()

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
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Search YouTube or YouTube Music") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Extractor.SearchFilter.entries.forEach { filter ->
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { vm.search(filter) },
                    label = { Text(filter.label) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        when {
            query.isNotBlank() && !searched && suggestions.isNotEmpty() -> {
                LazyColumn {
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
                            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.width(28.dp))
                            Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            error != null && items.isEmpty() -> ErrorState(error.orEmpty(), onRetry = { vm.search() })

            loading && items.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
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
                    items(items, key = { it.itemKey }) { item ->
                        when (item) {
                            is Extractor.SearchItem.Video -> VideoRow(
                                ref = item.stream,
                                onClick = { onOpenVideo(item.stream, emptyList()) },
                            )
                            is Extractor.SearchItem.Channel -> ChannelRow(
                                channel = item.channel,
                                onClick = { onOpenChannel(item.channel.url) },
                                subtitle = item.channel.subscriberCount
                                    .let { if (it > 0) "${app.librepipes.util.Format.count(it)} subscribers" else null },
                            )
                            is Extractor.SearchItem.Playlist -> ChannelRow(
                                channel = app.librepipes.data.model.ChannelRef(
                                    id = item.playlist.id,
                                    name = item.playlist.name,
                                    url = item.playlist.url,
                                    avatarUrl = item.playlist.thumbnailUrl,
                                ),
                                onClick = { onOpenPlaylist(item.playlist.url) },
                                subtitle = "${item.playlist.streamCount} videos",
                            )
                        }
                    }
                    if (hasMore) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.height(28.dp).width(28.dp))
                            }
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

private val Extractor.SearchItem.itemKey: String
    get() = when (this) {
        is Extractor.SearchItem.Video -> "v-${stream.id}"
        is Extractor.SearchItem.Channel -> "c-${channel.id}"
        is Extractor.SearchItem.Playlist -> "p-${playlist.id}"
    }
