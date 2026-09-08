package app.librepipes.ui.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.PlaylistRef
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.kit.LpBottomBar
import app.librepipes.ui.components.kit.LpChannelRow
import app.librepipes.ui.components.kit.LpCheckbox
import app.librepipes.ui.components.kit.LpContextMenu
import app.librepipes.ui.components.kit.LpDialog
import app.librepipes.ui.components.kit.LpDurationBadge
import app.librepipes.ui.components.kit.LpFilledButton
import app.librepipes.ui.components.kit.LpFilterChip
import app.librepipes.ui.components.kit.LpIconAction
import app.librepipes.ui.components.kit.LpIconButton
import app.librepipes.ui.components.kit.LpLinearProgress
import app.librepipes.ui.components.kit.LpMenuItem
import app.librepipes.ui.components.kit.LpMiniPlayer
import app.librepipes.ui.components.kit.LpNavItem
import app.librepipes.ui.components.kit.LpOutlinedButton
import app.librepipes.ui.components.kit.LpOutlinedTextField
import app.librepipes.ui.components.kit.LpPillButton
import app.librepipes.ui.components.kit.LpPlaylistRow
import app.librepipes.ui.components.kit.LpRadioButton
import app.librepipes.ui.components.kit.LpRoundIconButton
import app.librepipes.ui.components.kit.LpSearchBar
import app.librepipes.ui.components.kit.LpSecondaryButton
import app.librepipes.ui.components.kit.LpSeekBar
import app.librepipes.ui.components.kit.LpSheet
import app.librepipes.ui.components.kit.LpSkeletonBox
import app.librepipes.ui.components.kit.LpSkeletonRow
import app.librepipes.ui.components.kit.LpSnackbar
import app.librepipes.ui.components.kit.LpStatusPill
import app.librepipes.ui.components.kit.LpSwitch
import app.librepipes.ui.components.kit.LpTextButton
import app.librepipes.ui.components.kit.LpTopBar
import app.librepipes.ui.components.kit.LpVideoCard
import app.librepipes.ui.components.kit.LpVideoRow
import app.librepipes.ui.theme.LibrePipeTheme
import app.librepipes.ui.theme.Motion
import app.librepipes.ui.theme.ShapeTokens

/**
 * Debug-only gallery of every kit component (design board 02), light and dark.
 * Not exported, debug builds only — never shipped in release.
 */
class ComponentGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var dark by remember { mutableStateOf(false) }
            LibrePipeTheme(darkTheme = dark, dynamicColor = false) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    LpTopBar(
                        title = "Kit gallery",
                        navigationIcon = Icons.Rounded.ArrowBack,
                        onNavigationClick = { finish() },
                        actions = listOf(
                            LpIconAction(
                                icon = Icons.Rounded.ArrowForward,
                                contentDescription = "Toggle theme",
                                onClick = { dark = !dark },
                            ),
                        ),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Section("Buttons") { ButtonsSection() }
                        Section("Inputs") { InputsSection() }
                        Section("Cards & rows") { RowsSection() }
                        Section("Navigation") { NavigationSection() }
                        Section("Overlays") { OverlaysSection() }
                        Section("Player") { PlayerSection() }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

private val sampleStream = StreamRef(
    id = "v0",
    title = "Designing Material You Components with Compose",
    url = "https://youtube.com/watch?v=v0",
    thumbnailUrl = null,
    uploaderName = "Material Design",
    duration = 752,
    viewCount = 1_234_567,
    textualDate = "Mar 5, 2026",
    isLive = false,
)

private val liveStream = sampleStream.copy(id = "v1", title = "Live: Compose Camp Day 1", isLive = true, duration = 0)

private val sampleChannel = ChannelRef(
    id = "c0",
    name = "Material Design",
    url = "https://youtube.com/@material",
    avatarUrl = null,
    subscriberCount = 5_000_000,
)

private val samplePlaylist = PlaylistRef(
    id = "p0",
    name = "Compose Fundamentals",
    url = "https://youtube.com/playlist?list=p0",
    thumbnailUrl = null,
    uploaderName = "Material Design",
    streamCount = 42,
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun ButtonsSection() {
    LpFilledButton(text = "Filled", onClick = {})
    LpSecondaryButton(text = "Secondary", onClick = {})
    LpOutlinedButton(text = "Outlined", onClick = {})
    LpTextButton(text = "Text", onClick = {})
    LpTextButton(text = "Destructive", destructive = true, onClick = {})
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LpFilledButton(text = "Icon", leadingIcon = Icons.Rounded.Download, onClick = {})
        LpSecondaryButton(text = "Icon", leadingIcon = Icons.Rounded.Share, onClick = {})
        LpIconButton(icon = Icons.Rounded.Download, contentDescription = "Download", onClick = {})
    }
    LpFilledButton(text = "Disabled", onClick = {}, enabled = false)
}

@Composable
private fun InputsSection() {
    LpSearchBar(value = "", onValueChange = {}, placeholder = "Search")
    LpSearchBar(value = "compose", onValueChange = {}, placeholder = "Search")
    LpSearchBar(
        value = "kernel module signing",
        onValueChange = {},
        placeholder = "Search",
        onBack = {},
        onVoice = {},
    )
    LpOutlinedTextField(value = "", onValueChange = {}, label = "Playlist name")
    var radio by remember { mutableStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LpRadioButton(selected = radio == 0, onClick = { radio = 0 })
        LpRadioButton(selected = radio == 1, onClick = { radio = 1 })
    }
    LpSwitch(checked = true, onCheckedChange = {})
    LpSwitch(checked = false, onCheckedChange = {})
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LpCheckbox(checked = true, onCheckedChange = {})
        LpCheckbox(checked = false, onCheckedChange = {})
    }
    LpFilterChip(text = "Selected", selected = true, onClick = {})
    LpFilterChip(text = "Unselected", selected = false, onClick = {})
    LpFilterChip(text = "Dropdown", selected = false, onClick = {}, dropdown = true)
}

@Composable
private fun RowsSection() {
    LpVideoCard(ref = sampleStream, onClick = {}, width = 200.dp)
    LpVideoCard(ref = sampleStream, onClick = {}, width = 200.dp, progress = 0.4f)
    LpVideoCard(ref = liveStream, onClick = {}, width = 200.dp)
    LpVideoRow(ref = sampleStream, onClick = {}, progress = 0.3f)
    LpChannelRow(channel = sampleChannel, onClick = {}, subtitle = "5M subscribers", trailingLabel = "Subscribed")
    LpPlaylistRow(playlist = samplePlaylist, onClick = {})
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LpDurationBadge(text = "12:32")
        LpDurationBadge(text = "12:32", style = app.librepipes.ui.components.kit.LpBadgeStyle.Tiny)
        LpStatusPill(text = "LIVE", isLive = true)
        LpStatusPill(text = "Up next")
    }
    LpSkeletonRow()
    LpSkeletonBox(modifier = Modifier.width(200.dp).height(112.dp))
    LpLinearProgress(progress = 0.5f)
}

@Composable
private fun NavigationSection() {
    LpBottomBar(
        items = listOf(
            LpNavItem("Home", Icons.Rounded.Home, Icons.Outlined.Home, unreadCount = 3),
            LpNavItem("Search", Icons.Rounded.Search, Icons.Outlined.Search),
            LpNavItem("Downloads", Icons.Rounded.Download, Icons.Outlined.Download),
        ),
        selectedIndex = 0,
        onSelect = {},
    )
    LpRoundIconButton(icon = Icons.Rounded.ArrowBack, contentDescription = "Back", onClick = {})
}

@Composable
private fun OverlaysSection() {
    var showDialog by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    LpOutlinedButton(text = "Open dialog", onClick = { showDialog = true })
    LpOutlinedButton(text = "Open sheet", onClick = { showSheet = true })
    LpOutlinedButton(text = "Open menu", onClick = { showMenu = true })
    LpSnackbar(message = "Removed from history", onDismiss = {}, onUndo = {})
    if (showDialog) {
        LpDialog(
            title = "Delete download",
            text = "This removes the file from your device.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
    if (showSheet) {
        LpSheet(title = "Playback speed", onDismiss = { showSheet = false }) {
            listOf("0.5x", "0.75x", "Normal", "1.25x", "1.5x", "2x").forEach { speed ->
                LpFilledButton(text = speed, onClick = {}, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (showMenu) {
        LpContextMenu(
            title = "More options",
            onDismiss = { showMenu = false },
            items = listOf(
                LpMenuItem("Add to playlist", Icons.Rounded.Share) { showMenu = false },
                LpMenuItem("Hide", Icons.Rounded.VisibilityOff) { showMenu = false },
                LpMenuItem("Remove", Icons.Rounded.Delete, destructive = true) { showMenu = false },
            ),
        )
    }
}

@Composable
private fun PlayerSection() {
    LpSeekBar(progress = 0.4f, onSeek = {}, chapters = listOf(0.1f, 0.45f, 0.8f))
    LpMiniPlayer(
        title = sampleStream.title,
        channelName = sampleStream.uploaderName,
        thumbnailUrl = null,
        progress = 0.35f,
        onClick = {},
        onClose = {},
    )
    LpStatusPill(text = "4K")
}
