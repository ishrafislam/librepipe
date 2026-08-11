package app.librepipes.ui.components.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.ui.theme.ShapeTokens
import app.librepipes.ui.theme.Spacing
import coil3.compose.AsyncImage

/**
 * Design board 02-F — Player components.
 */

/** Brand blue used for the played portion of the seek bar — the overlay is theme-independent. */
val LpSeekPlayed = Color(0xFF4AA8E8)

/**
 * Seek bar: played = brand blue, 12dp thumb (6x16 pill while dragging),
 * optional chapter ticks (2x8, white 90%).
 */
@Composable
fun LpSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    chapters: List<Float> = emptyList(),
) {
    // While the finger is down the bar follows the touch, not the player — otherwise
    // position updates arriving mid-drag would yank the thumb back.
    var dragging by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) scrub else progress.coerceIn(0f, 1f)
    val thumbSize by animateDpAsState(if (dragging) 6.dp else 12.dp, label = "seek-thumb")
    val played = LpSeekPlayed
    val track = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFFD8DCE0) else Color(0xFF3E4347)
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(2.dp))
            .semantics { contentDescription = "Seek bar" },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            scrub = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragging = false
                            onSeek(scrub)
                        },
                        onDragCancel = { dragging = false },
                    )
                },
        ) {
            val trackY = size.height / 2f
            val stroke = with(density) { 4.dp.toPx() }
            drawLine(track, Offset(0f, trackY), Offset(size.width, trackY), strokeWidth = stroke)
            val playedWidth = size.width * shown
            drawLine(played, Offset(0f, trackY), Offset(playedWidth, trackY), strokeWidth = stroke)
            chapters.forEach { chapter ->
                val x = size.width * chapter.coerceIn(0f, 1f)
                if (x in 0f..size.width) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.9f),
                        topLeft = Offset(x - 1f, trackY - 4f),
                        size = Size(2f, 8f),
                    )
                }
            }
            val thumbPx = with(density) { thumbSize.toPx() }
            if (dragging) {
                drawRoundRect(
                    color = played,
                    topLeft = Offset(playedWidth - thumbPx / 2f, trackY - 8f),
                    size = Size(thumbPx, 16f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbPx / 2f),
                )
            } else {
                drawCircle(played, radius = thumbPx / 2f, center = Offset(playedWidth, trackY))
            }
        }
    }
}

/**
 * Floating 72dp mini player: rounded surfaceContainerHigh card inset from the screen
 * edges, 96x54 thumb (radius 8), play/pause + close, 2dp progress along the card's
 * bottom edge.
 */
@Composable
fun LpMiniPlayer(
    title: String,
    channelName: String?,
    thumbnailUrl: String?,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onPlayPause: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    // Room for however many trailing buttons are actually shown, so long titles
    // ellipsize instead of sliding under them.
    val trailingWidth = 8.dp + 40.dp * listOfNotNull(onPlayPause, onClose).size
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.space3, vertical = Spacing.space2)
            .height(72.dp)
            // clip before background, or the background paints square corners underneath.
            .clip(ShapeTokens.md)
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .width(96.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = trailingWidth),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!channelName.isNullOrBlank()) {
                    Text(
                        text = channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onPlayPause != null) {
                MiniPlayerAction(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    onClick = onPlayPause,
                )
            }
            if (onClose != null) {
                MiniPlayerAction(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close",
                    onClick = onClose,
                )
            }
        }

        val track = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF42474E) else Color(0xFF9CCBFA)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.surfaceContainer),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(track),
            )
        }
    }
}

@Composable
private fun MiniPlayerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .size(40.dp)
            .clip(ShapeTokens.full)
            .clickable(onClick = onClick)
            .padding(9.dp),
    )
}

/** Status pill on artwork: white 8% background, LIVE = brand red text. */
@Composable
fun LpStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    isLive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (isLive) Color(0xFFFF4B3E) else colors.onSurface,
        modifier = modifier
            .clip(ShapeTokens.full)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
