package app.librepipes.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import app.librepipes.ui.components.EmptyState
import app.librepipes.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onRequestNotificationPermission: () -> Unit,
) {
    val theme by vm.settings.theme.collectAsState(initial = 0)
    val maxQuality by vm.settings.maxQuality.collectAsState(initial = 1080)
    val audioOnly by vm.settings.audioOnly.collectAsState(initial = false)
    val captions by vm.settings.captionsEnabled.collectAsState(initial = true)
    val recordHistory by vm.settings.recordHistory.collectAsState(initial = true)
    val notifications by vm.settings.notificationsEnabled.collectAsState(initial = true)
    val refreshInterval by vm.settings.refreshIntervalHours.collectAsState(initial = 6)
    val downloadQuality by vm.settings.downloadQuality.collectAsState(initial = 1080)

    val qualities = listOf(0 to "Auto", 360 to "360p", 480 to "480p", 720 to "720p", 1080 to "1080p", 1440 to "1440p", 2160 to "2160p")
    val intervals = listOf(2 to "Every 2 hours", 6 to "Every 6 hours", 12 to "Every 12 hours", 24 to "Daily")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
    ) {
        item { SectionLabel("Appearance") }
        item {
            DropdownSetting(
                label = "Theme",
                options = listOf("Follow system", "Light", "Dark"),
                selectedIndex = theme.coerceIn(0, 2),
                onSelect = { vm.setTheme(it) },
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionLabel("Playback") }
        item {
            DropdownSetting(
                label = "Maximum video quality",
                options = qualities.map { it.second },
                selectedIndex = qualities.indexOfFirst { it.first == maxQuality }.coerceAtLeast(0),
                onSelect = { vm.setMaxQuality(qualities[it].first) },
            )
        }
        item { SwitchSetting("Start audio-only", audioOnly, vm::setAudioOnly, "Play only the audio track when opening a video") }
        item { SwitchSetting("Show subtitles", captions, vm::setCaptionsEnabled, "Load caption tracks when available") }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionLabel("Data & privacy") }
        item { SwitchSetting("Record watch history", recordHistory, vm::setRecordHistory, "Remember playback positions for resuming") }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Clear watch history", modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.clearHistory() }) { Text("Clear") }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Clear downloads", modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.clearDownloads() }) { Text("Clear") }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionLabel("Notifications") }
        item {
            SwitchSetting(
                "New upload notifications",
                notifications,
                { enabled ->
                    vm.setNotificationsEnabled(enabled)
                    if (enabled) onRequestNotificationPermission()
                },
                "Checks your subscriptions in the background (no Google services)",
            )
        }
        item {
            DropdownSetting(
                label = "Check interval",
                options = intervals.map { it.second },
                selectedIndex = intervals.indexOfFirst { it.first == refreshInterval }.coerceAtLeast(0),
                onSelect = { vm.setRefreshInterval(intervals[it].first) },
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionLabel("Downloads") }
        item {
            DropdownSetting(
                label = "Download quality",
                options = qualities.map { it.second },
                selectedIndex = qualities.indexOfFirst { it.first == downloadQuality }.coerceAtLeast(0),
                onSelect = { vm.setDownloadQuality(qualities[it].first) },
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { SectionLabel("About") }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("LibrePipe", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Free, open-source YouTube & YouTube Music client. No ads, no tracking, no login.\nGPL-3.0 — extraction powered by an in-app InnerTube client.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun SwitchSetting(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSetting(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = options.getOrElse(selectedIndex) { options.first() },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}
