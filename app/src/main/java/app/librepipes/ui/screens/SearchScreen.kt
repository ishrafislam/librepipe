package app.librepipes.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.data.db.SearchHistoryEntity
import app.librepipes.data.extractor.Extractor
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.kit.LpChannelRow
import app.librepipes.ui.components.kit.LpEmptyState
import app.librepipes.ui.components.kit.LpErrorState
import app.librepipes.ui.components.kit.LpListSkeleton
import app.librepipes.ui.components.kit.LpPlaylistRow
import app.librepipes.ui.components.kit.LpSearchBar
import app.librepipes.ui.components.kit.LpTextButton
import app.librepipes.ui.components.kit.LpVideoRow
import app.librepipes.ui.components.kit.rememberDelayedSkeleton
import app.librepipes.ui.theme.ShapeTokens
import app.librepipes.ui.theme.Spacing
import app.librepipes.ui.viewmodels.SearchViewModel
import app.librepipes.util.AppError
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
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Search YouTube")
    }
    // Not every device ships a recognizer; without this the mic would crash on tap.
    val voiceAvailable = remember {
        @Suppress("DEPRECATION")
        context.packageManager.resolveActivity(voiceIntent, 0) != null
    }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
        if (spoken != null) {
            keyboard?.hide()
            vm.search(spoken)
        }
    }

    LaunchedEffect(listState, vm.items.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }
            .distinctUntilChanged()
            .collect { last ->
                if (vm.items.isNotEmpty() && last >= vm.items.size - 4 && vm.hasMore) vm.loadMore()
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LpSearchBar(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            onBack = onBack,
            placeholder = "Search Librepipe",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    vm.search()
                },
            ),
            onVoice = if (voiceAvailable) {
                { voiceLauncher.launch(voiceIntent) }
            } else {
                null
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )

        when (vm.mode) {
            SearchViewModel.Mode.RESULTS -> ResultsSection(
                vm = vm,
                listState = listState,
                onOpenVideo = onOpenVideo,
                onOpenChannel = onOpenChannel,
                onOpenPlaylist = onOpenPlaylist,
            )

            SearchViewModel.Mode.RECENTS, SearchViewModel.Mode.SUGGESTIONS -> LandingSection(
                suggestions = vm.suggestions,
                recents = vm.recents,
                onSubmit = { text ->
                    keyboard?.hide()
                    vm.onSuggestionClick(text)
                },
                onFill = vm::onQueryChange,
                onRemoveRecent = vm::removeRecent,
                onClearRecents = vm::clearRecents,
            )
        }
    }
}

/**
 * Landing view: suggestions above recents, then the privacy note. Empty suggestions
 * stay here rather than falling through to results — the user always has somewhere to go.
 */
@Composable
private fun LandingSection(
    suggestions: List<String>,
    recents: List<SearchHistoryEntity>,
    onSubmit: (String) -> Unit,
    onFill: (String) -> Unit,
    onRemoveRecent: (Long) -> Unit,
    onClearRecents: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    if (suggestions.isEmpty() && recents.isEmpty()) {
        LpEmptyState(
            icon = Icons.Rounded.Search,
            title = "Search Librepipe",
            message = "Find videos, channels and playlists on YouTube.",
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(suggestions, key = { "s-$it" }) { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSubmit(suggestion) }
                    // No vertical padding: the 48dp trailing icon already sets row height.
                    .padding(start = Spacing.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(Spacing.space4))
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Rounded.NorthWest,
                    contentDescription = "Edit this search",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(ShapeTokens.full)
                        .clickable { onFill(suggestion) }
                        .padding(12.dp),
                )
            }
        }

        if (recents.isNotEmpty()) {
            if (suggestions.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Spacing.space4, vertical = 4.dp),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.space4, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent on this device",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    LpTextButton(text = "Clear", onClick = onClearRecents)
                }
            }
            items(recents, key = { it.id }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSubmit(entry.query) }
                        // No vertical padding: the 48dp trailing icon already sets row height.
                    .padding(start = Spacing.space4),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(Spacing.space4))
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
                            .clip(ShapeTokens.full)
                            .clickable { onRemoveRecent(entry.id) }
                            .padding(12.dp),
                    )
                }
            }
        }

        item { PrivacyNote() }
    }
}

@Composable
private fun PrivacyNote() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space4, vertical = Spacing.space4)
            .clip(ShapeTokens.md)
            .background(colors.surfaceContainer)
            .padding(Spacing.space4),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Suggestions are fetched anonymously. Searches are stored on this " +
                "device only and never leave it.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultsSection(
    vm: SearchViewModel,
    listState: LazyListState,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val items = vm.items
    val loading = vm.loading
    val error: AppError? = vm.error
    val skeleton = rememberDelayedSkeleton(loading && items.isEmpty())
    when {
        error != null && items.isEmpty() -> LpErrorState(
            message = error.message,
            code = error.code,
            onRetry = { vm.search() },
        )

        loading && items.isEmpty() ->
            if (skeleton) LpListSkeleton() else Box(Modifier.fillMaxSize())

        items.isEmpty() -> LpEmptyState(
            icon = Icons.Rounded.Search,
            title = "No results",
            message = "Try different keywords.",
        )

        else -> {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(items, key = { it.key() }) { item ->
                    when (item) {
                        is Extractor.SearchItem.Video -> LpVideoRow(
                            ref = item.stream,
                            onClick = { onOpenVideo(item.stream, emptyList()) },
                            showMenu = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )

                        is Extractor.SearchItem.Channel -> {
                            val subscribed = item.channel.url in vm.subscribedUrls
                            LpChannelRow(
                                channel = item.channel,
                                onClick = { onOpenChannel(item.channel.url) },
                                subtitle = channelSubtitle(
                                    item.channel.subscriberCount,
                                    item.channel.handle,
                                ),
                                trailingLabel = if (subscribed) "Subscribed" else "Subscribe",
                                trailingFilled = !subscribed,
                                onTrailingClick = { vm.toggleSubscribe(item.channel) },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(
                                    horizontal = Spacing.space4,
                                    vertical = 8.dp,
                                ),
                            )
                        }

                        is Extractor.SearchItem.Playlist -> LpPlaylistRow(
                            playlist = item.playlist,
                            onClick = { onOpenPlaylist(item.playlist.url) },
                            subtitle = "${item.playlist.streamCount} videos",
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
                if (vm.hasMore) {
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

/** "21.1M subscribers · @mkbhd", dropping whichever half is missing. */
private fun channelSubtitle(subscriberCount: Long, handle: String?): String? =
    listOfNotNull(
        "${Format.count(subscriberCount)} subscribers".takeIf { subscriberCount > 0 },
        handle,
    ).joinToString(" · ").takeIf { it.isNotEmpty() }
