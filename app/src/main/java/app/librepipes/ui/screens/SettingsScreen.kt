package app.librepipes.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.ui.components.kit.LpIconAction
import app.librepipes.ui.components.kit.LpSheet
import app.librepipes.ui.components.kit.LpSwitch
import app.librepipes.ui.components.kit.LpTopBar
import app.librepipes.ui.theme.PlexMono
import app.librepipes.ui.viewmodels.SettingsViewModel
import app.librepipes.ui.viewmodels.UpdateViewModel
import app.librepipes.BuildConfig

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    updateVm: UpdateViewModel,
    onRequestNotificationPermission: () -> Unit,
) {
    val theme by vm.settings.theme.collectAsState(initial = 0)
    val dynamicColor by vm.settings.dynamicColor.collectAsState(initial = true)
    val maxQuality by vm.settings.maxQuality.collectAsState(initial = 1080)
    val audioOnly by vm.settings.audioOnly.collectAsState(initial = false)
    val captions by vm.settings.captionsEnabled.collectAsState(initial = true)
    val recordHistory by vm.settings.recordHistory.collectAsState(initial = true)
    val notifications by vm.settings.notificationsEnabled.collectAsState(initial = true)
    val refreshInterval by vm.settings.refreshIntervalHours.collectAsState(initial = 6)
    val downloadQuality by vm.settings.downloadQuality.collectAsState(initial = 1080)
    val updateState by updateVm.uiState.collectAsState()

    val qualities = listOf(0 to "Auto", 360 to "360p", 480 to "480p", 720 to "720p", 1080 to "1080p", 1440 to "1440p", 2160 to "2160p")
    val intervals = listOf(2 to "Every 2 hours", 6 to "Every 6 hours", 12 to "Every 12 hours", 24 to "Daily")
    val themeOptions = listOf("Follow system", "Light", "Dark")

    var picker by remember { mutableStateOf<PickerKind?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            LpTopBar(
                title = "Settings",
                actions = listOf(
                    LpIconAction(Icons.Rounded.MoreVert, null) {},
                ),
            )
        }
        item { SettingsSectionHeader("Appearance") }
        item {
            SettingsRow(
                icon = Icons.Rounded.Palette,
                label = "Theme",
                value = themeOptions.getOrElse(theme.coerceIn(0, 2)) { "System" },
                onClick = { picker = PickerKind.Theme },
            )
        }
        item {
            SettingsSwitchRow(
                icon = Icons.Rounded.DarkMode,
                label = "Dynamic colours",
                subtitle = "Tint the app with your wallpaper",
                checked = dynamicColor,
                onChange = vm::setDynamicColor,
            )
        }

        item { SettingsSectionHeader("Playback") }
        item {
            SettingsRow(
                icon = Icons.Rounded.PlayCircle,
                label = "Maximum quality",
                value = qualities.firstOrNull { it.first == maxQuality }?.second ?: "Auto",
                onClick = { picker = PickerKind.Quality },
            )
        }
        item {
            SettingsSwitchRow(
                icon = Icons.Rounded.PlayCircle,
                label = "Start audio-only",
                subtitle = "Play only the audio track when opening a video",
                checked = audioOnly,
                onChange = vm::setAudioOnly,
            )
        }
        item {
            SettingsSwitchRow(
                icon = Icons.Rounded.PlayCircle,
                label = "Show subtitles",
                subtitle = "Load caption tracks when available",
                checked = captions,
                onChange = vm::setCaptionsEnabled,
            )
        }

        item { SettingsSectionHeader("Data & privacy") }
        item {
            SettingsSwitchRow(
                icon = Icons.Rounded.Lock,
                label = "Record watch history",
                subtitle = "Remember playback positions for resuming",
                checked = recordHistory,
                onChange = vm::setRecordHistory,
            )
        }
        item {
            SettingsActionRow(
                icon = Icons.Rounded.Lock,
                label = "Clear watch history",
                action = { TextButton(onClick = { vm.clearHistory() }) { Text("Clear") } },
            )
        }
        item {
            SettingsActionRow(
                icon = Icons.Rounded.Lock,
                label = "Clear downloads",
                action = { TextButton(onClick = { vm.clearDownloads() }) { Text("Clear") } },
            )
        }

        item { SettingsSectionHeader("Notifications") }
        item {
            SettingsSwitchRow(
                icon = Icons.Rounded.Notifications,
                label = "New upload notifications",
                subtitle = "Checks your subscriptions in the background (no Google services)",
                checked = notifications,
                onChange = { enabled ->
                    vm.setNotificationsEnabled(enabled)
                    if (enabled) onRequestNotificationPermission()
                },
            )
        }
        item {
            SettingsRow(
                icon = Icons.Rounded.Notifications,
                label = "Check interval",
                value = intervals.firstOrNull { it.first == refreshInterval }?.second ?: "Every 6 hours",
                onClick = { picker = PickerKind.Interval },
            )
        }

        item { SettingsSectionHeader("Downloads") }
        item {
            SettingsRow(
                icon = Icons.Rounded.Download,
                label = "Download quality",
                value = qualities.firstOrNull { it.first == downloadQuality }?.second ?: "Auto",
                onClick = { picker = PickerKind.DownloadQuality },
            )
        }

        item { SettingsSectionHeader("About") }
        item {
            SettingsRow(
                icon = Icons.Rounded.Info,
                label = "LibrePipe",
                value = "v${BuildConfig.VERSION_NAME}",
                onClick = {},
            )
        }
        item {
            val updateValue = when {
                updateState.debugDisabled -> "Debug build"
                updateState.checking -> "Checking…"
                updateState.downloading -> "${updateState.progress}%"
                updateState.installReadyPath != null -> "Ready"
                updateState.release != null -> "v${updateState.release?.versionName}"
                updateState.upToDate -> "Up to date"
                updateState.error != null -> "Retry"
                else -> null
            }
            SettingsRow(
                icon = Icons.Rounded.SystemUpdate,
                label = "Check for updates",
                value = updateValue,
                onClick = updateVm::showAvailableUpdate,
            )
        }
        item {
            Text(
                text = "Free, open-source YouTube & YouTube Music client. No ads, no tracking, no login. GPL-3.0.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    when (picker) {
        PickerKind.Theme -> OptionSheet(
            title = "Theme",
            options = themeOptions,
            selectedIndex = theme.coerceIn(0, 2),
            onSelect = { vm.setTheme(it) },
            onDismiss = { picker = null },
        )
        PickerKind.Quality -> OptionSheet(
            title = "Maximum quality",
            options = qualities.map { it.second },
            selectedIndex = qualities.indexOfFirst { it.first == maxQuality }.coerceAtLeast(0),
            onSelect = { vm.setMaxQuality(qualities[it].first) },
            onDismiss = { picker = null },
        )
        PickerKind.Interval -> OptionSheet(
            title = "Check interval",
            options = intervals.map { it.second },
            selectedIndex = intervals.indexOfFirst { it.first == refreshInterval }.coerceAtLeast(0),
            onSelect = { vm.setRefreshInterval(intervals[it].first) },
            onDismiss = { picker = null },
        )
        PickerKind.DownloadQuality -> OptionSheet(
            title = "Download quality",
            options = qualities.map { it.second },
            selectedIndex = qualities.indexOfFirst { it.first == downloadQuality }.coerceAtLeast(0),
            onSelect = { vm.setDownloadQuality(qualities[it].first) },
            onDismiss = { picker = null },
        )
        null -> {}
    }
}

private enum class PickerKind { Theme, Quality, Interval, DownloadQuality }

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = PlexMono,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    label: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        LpSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    label: String,
    action: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        action()
    }
}

@Composable
private fun OptionSheet(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    LpSheet(title = title, onDismiss = onDismiss) {
        options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable {
                        onSelect(index)
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (index == selectedIndex) {
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
