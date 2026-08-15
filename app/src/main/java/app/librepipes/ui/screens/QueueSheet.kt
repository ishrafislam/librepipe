package app.librepipes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.components.kit.LpSheet
import app.librepipes.ui.components.kit.LpSwitch
import app.librepipes.ui.components.kit.LpTextButton
import app.librepipes.ui.theme.ShapeTokens
import app.librepipes.ui.theme.Spacing
import app.librepipes.ui.viewmodels.WatchViewModel
import app.librepipes.util.Format
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

private val ROW_HEIGHT = 76.dp

/**
 * Docked above the system bar on the watch page, in the same shape language as the mini
 * player: what is coming next, and a way into the full queue.
 */
@Composable
fun QueueBar(
    count: Int,
    nextTitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.space3,
                end = Spacing.space3,
                top = Spacing.space2,
                bottom = Spacing.space3,
            )
            .height(64.dp)
            .clip(ShapeTokens.md)
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.PlaylistPlay,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Queue · $count videos",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!nextTitle.isNullOrBlank()) {
                Text(
                    text = "Next: $nextTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun QueueSheet(vm: WatchViewModel, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val queue = vm.queue
    val current = vm.queueIndex
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT.toPx() }

    // Drag state lives here rather than per row: a row that moves under the finger would
    // otherwise lose its own gesture.
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LpSheet(
        title = "Queue",
        onDismiss = onDismiss,
        fullHeight = true,
        action = {
            LpTextButton(
                text = "Clear",
                onClick = { vm.clearQueue() },
                modifier = Modifier.padding(end = Spacing.space2),
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.space5, vertical = Spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlaylistPlay,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.space4))
            Column(modifier = Modifier.weight(1f)) {
                Text("Autoplay", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Play the next item automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            LpSwitch(checked = vm.autoplay, onCheckedChange = vm::toggleAutoplay)
        }

        queue.forEachIndexed { index, ref ->
            val isCurrent = index == current
            val dragging = index == draggingIndex
            QueueRow(
                ref = ref,
                isCurrent = isCurrent,
                dragging = dragging,
                remainingMs = if (isCurrent) (vm.duration - vm.position).coerceAtLeast(0L) else 0L,
                onClick = { vm.playQueueItem(index) },
                onRemove = { vm.removeQueueItem(index) },
                modifier = Modifier
                    .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) },
                dragHandle = if (isCurrent) {
                    null
                } else {
                    Modifier.pointerInput(index, queue.size) {
                        detectDragGestures(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                            },
                            onDragEnd = {
                                val target = (index + (dragOffset / rowHeightPx).roundToInt())
                                    .coerceIn(0, queue.size - 1)
                                draggingIndex = -1
                                dragOffset = 0f
                                vm.moveQueueItem(index, target)
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffset = 0f
                            },
                        )
                    }
                },
            )
        }

        Text(
            text = "Drag a row by its handle to reorder. The queue is cleared when the " +
                "app closes unless you save it as a playlist.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = Spacing.space5,
                vertical = Spacing.space4,
            ),
        )
    }
}

@Composable
private fun QueueRow(
    ref: StreamRef,
    isCurrent: Boolean,
    dragging: Boolean,
    remainingMs: Long,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: Modifier?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(
                when {
                    dragging -> colors.surfaceContainerHighest
                    isCurrent -> colors.primaryContainer
                    else -> colors.surface
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (dragHandle != null) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = "Reorder",
                    tint = colors.onSurfaceVariant,
                    modifier = dragHandle,
                )
            }
        }
        AsyncImage(
            model = ref.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .width(88.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(Spacing.space3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ref.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) colors.onPrimaryContainer else colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (isCurrent) {
                    "Now playing · ${Format.time(remainingMs)} left"
                } else {
                    listOfNotNull(
                        ref.uploaderName?.takeIf { it.isNotBlank() },
                        ref.duration.takeIf { it > 0 }?.let { Format.durationSeconds(it) },
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isCurrent) colors.onPrimaryContainer else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!isCurrent) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove from queue",
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .clip(ShapeTokens.full)
                    .clickable(onClick = onRemove)
                    .padding(9.dp),
            )
        }
    }
}
