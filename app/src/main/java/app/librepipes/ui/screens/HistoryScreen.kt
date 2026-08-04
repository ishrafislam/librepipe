package app.librepipes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.kit.LpContextMenu
import app.librepipes.ui.components.kit.LpDialog
import app.librepipes.ui.components.kit.LpEmptyState
import app.librepipes.ui.components.kit.LpIconAction
import app.librepipes.ui.components.kit.LpMenuItem
import app.librepipes.ui.components.kit.LpTopBar
import app.librepipes.ui.components.kit.LpVideoRow
import app.librepipes.ui.theme.ShapeTokens
import app.librepipes.ui.viewmodels.HistoryViewModel
import app.librepipes.util.Format
import java.util.Calendar

@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
    onBack: () -> Unit,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
) {
    val entries = vm.entries
    val recording = vm.recording
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LpTopBar(
            title = "History",
            onNavigationClick = onBack,
            navigationIcon = Icons.Rounded.ArrowBack,
            actions = listOf(
                LpIconAction(
                    icon = Icons.Rounded.DeleteSweep,
                    contentDescription = "Clear history",
                    onClick = { confirmClear = true },
                ),
            ),
        )

        if (entries.isEmpty()) {
            LpEmptyState(
                icon = Icons.Rounded.History,
                title = "No watch history",
                message = "Videos you watch will appear here.",
            )
            return@Column
        }

        if (!recording) {
            HistoryPausedBanner(vm)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            val groups = groupByDay(entries)
            groups.forEach { (label, group) ->
                item(key = "header_$label") {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(group, key = { it.entity.streamId }) { entry ->
                    val ref = entry.ref
                    if (ref != null) {
                        HistoryRow(vm, entry, ref, onOpenVideo)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        LpDialog(
            title = "Clear watch history?",
            text = "This will remove all videos from your history. This can't be undone.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                vm.clear()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun HistoryPausedBanner(vm: HistoryViewModel) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(ShapeTokens.md)
            .background(colors.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Watch history is paused. New views won't be recorded.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { vm.setRecording(true) }) {
            Text("Resume")
        }
    }
}

@Composable
private fun HistoryRow(
    vm: HistoryViewModel,
    entry: HistoryViewModel.HistoryEntry,
    ref: StreamRef,
    onOpenVideo: (StreamRef, List<StreamRef>) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val entity = entry.entity
    val progress = if (entity.durationMs > 0) {
        (entity.positionMs.toFloat() / entity.durationMs).coerceIn(0f, 1f)
    } else 0f
    val remainingMs = (entity.durationMs - entity.positionMs).coerceAtLeast(0L)

    LpVideoRow(
        ref = ref,
        onClick = { onOpenVideo(ref, emptyList()) },
        progress = progress,
        onLongPress = { showMenu = true },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
    Text(
        text = statusText(progress, remainingMs),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
    )

    if (showMenu) {
        Dialog(onDismissRequest = { showMenu = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showMenu = false },
                contentAlignment = Alignment.Center,
            ) {
                LpContextMenu(
                    title = ref.title,
                    items = listOf(
                        LpMenuItem(
                            label = "Remove from history",
                            icon = Icons.Rounded.DeleteSweep,
                            destructive = true,
                            onClick = { vm.remove(ref.id) },
                        ),
                    ),
                    onDismiss = { showMenu = false },
                )
            }
        }
    }
}

private fun statusText(progress: Float, remainingMs: Long): String =
    if (progress >= 0.98f) "Watched"
    else "${Format.time(remainingMs)} left"

private fun groupByDay(entries: List<HistoryViewModel.HistoryEntry>): List<Pair<String, List<HistoryViewModel.HistoryEntry>>> {
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val startOfToday = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startOfYesterday = startOfToday - 86_400_000L
    val startOfWeek = startOfToday - (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY) * 86_400_000L

    val sorted = entries.sortedByDescending { it.entity.watchedAt }
    return sorted.groupBy { entry ->
        when {
            entry.entity.watchedAt >= startOfToday -> "Today"
            entry.entity.watchedAt >= startOfYesterday -> "Yesterday"
            entry.entity.watchedAt >= startOfWeek -> "Earlier this week"
            else -> "Earlier"
        }
    }.map { (label, list) -> label to list }
}
