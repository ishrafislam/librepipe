package app.librepipes.ui.components.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.librepipes.data.model.ChannelRef
import app.librepipes.data.model.PlaylistRef
import app.librepipes.data.model.StreamRef
import app.librepipes.ui.theme.PlexMono
import app.librepipes.ui.theme.ShapeTokens
import app.librepipes.util.Format
import coil3.compose.AsyncImage

/**
 * Design board 02-C — Cards & list rows.
 * No card container, no border, no shadow — the thumbnail is the card.
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LpVideoCard(
    ref: StreamRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed card width; pass null to let the caller's [modifier] size the card. */
    width: Dp? = 200.dp,
    onLongPress: (() -> Unit)? = null,
    showChannel: Boolean = true,
    showAvatar: Boolean = true,
    progress: Float? = null,
    /** Opens the overflow menu. The glyph is inert when null. */
    onMenuClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Box {
            AsyncImage(
                model = ref.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(ShapeTokens.md),
                contentScale = ContentScale.Crop,
            )
            if (ref.isLive) {
                LpDurationBadge(
                    text = "LIVE",
                    color = colors.error,
                    style = LpBadgeStyle.Small,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
            if (ref.duration > 0) {
                LpDurationBadge(
                    text = Format.durationSeconds(ref.duration),
                    style = LpBadgeStyle.Small,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                )
            }
            if (progress != null && progress > 0f && progress < 1f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.2f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(colors.primary),
                    )
                }
            }
        }
        Row(modifier = Modifier.padding(top = 12.dp)) {
            if (showAvatar) {
                val avatarModifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh)
                if (ref.uploaderAvatarUrl != null) {
                    AsyncImage(
                        model = ref.uploaderAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = avatarModifier,
                    )
                } else {
                    Box(modifier = avatarModifier)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ref.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showChannel && !ref.uploaderName.isNullOrBlank()) {
                    Text(
                        text = ref.uploaderName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = Format.videoMeta(ref.viewCount, ref.textualDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = if (onMenuClick != null) "More options" else null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(if (onMenuClick != null) Modifier.clickable(onClick = onMenuClick) else Modifier)
                    .padding(13.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LpVideoRow(
    ref: StreamRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    progress: Float? = null,
    /** Trailing overflow glyph. Decorative today — it carries no click handler. */
    showMenu: Boolean = true,
    /** Off on a channel page, where every row has the same owner. */
    showChannel: Boolean = true,
    /** Opens the overflow menu. The glyph is inert when null. */
    onMenuClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Box {
            AsyncImage(
                model = ref.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(160.dp)
                    .aspectRatio(16f / 9f)
                    .clip(ShapeTokens.md),
                contentScale = ContentScale.Crop,
            )
            if (ref.isLive) {
                LpDurationBadge(
                    text = "LIVE",
                    color = colors.error,
                    style = LpBadgeStyle.Tiny,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            }
            if (ref.duration > 0) {
                LpDurationBadge(
                    text = Format.durationSeconds(ref.duration),
                    style = LpBadgeStyle.Tiny,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ref.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showChannel && !ref.uploaderName.isNullOrBlank()) {
                Text(
                    text = ref.uploaderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = Format.videoMeta(ref.viewCount, ref.textualDate),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
            if (progress != null && progress > 0f && progress < 1f) {
                Spacer(Modifier.height(4.dp))
                LpLinearProgress(progress = progress)
            }
        }
        if (showMenu) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = if (onMenuClick != null) "More options" else null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(if (onMenuClick != null) Modifier.clickable(onClick = onMenuClick) else Modifier)
                    .padding(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LpChannelRow(
    channel: ChannelRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    /** Render the trailing action as a filled call-to-action instead of an outlined pill. */
    trailingFilled: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = null)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = channel.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (trailingLabel != null) {
            val trailingModifier = Modifier.padding(start = 12.dp)
            if (trailingFilled) {
                LpFilledButton(
                    text = trailingLabel,
                    onClick = onTrailingClick ?: {},
                    modifier = trailingModifier,
                )
            } else {
                // Same 40dp/labelLarge geometry as the filled state, so toggling
                // subscribe changes the fill and nothing else.
                LpOutlinedButton(
                    text = trailingLabel,
                    onClick = onTrailingClick ?: {},
                    modifier = trailingModifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LpPlaylistRow(
    playlist: PlaylistRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    thumbnailWidth: Dp = 96.dp,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = null)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(thumbnailWidth)
                    .aspectRatio(16f / 9f)
                    .clip(ShapeTokens.md),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(ShapeTokens.md)
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = colors.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: "${Format.count(playlist.streamCount)} videos",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier
                .size(48.dp)
                .padding(14.dp),
        )
    }
}

// ------------------------------------------------------------------- Badges

enum class LpBadgeStyle {
    /** 11sp mono — feed card duration. */
    Small,

    /** 10sp mono — list-row duration. */
    Tiny,
}

@Composable
fun LpDurationBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Black.copy(alpha = 0.75f),
    style: LpBadgeStyle = LpBadgeStyle.Small,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = if (style == LpBadgeStyle.Small) MaterialTheme.typography.labelSmall.fontSize else 10.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = PlexMono,
        modifier = modifier
            .clip(ShapeTokens.xs)
            .background(color)
            .padding(
                horizontal = if (style == LpBadgeStyle.Small) 6.dp else 5.dp,
                vertical = if (style == LpBadgeStyle.Small) 3.dp else 2.dp,
            ),
    )
}

/** 32dp outlined pill — e.g. the "Subscribed" trailing button on a channel row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LpPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center,
        color = colors.onSurfaceVariant,
        modifier = modifier
            .height(32.dp)
            .clip(ShapeTokens.full)
            .border(1.dp, colors.outline, ShapeTokens.full)
            .combinedClickable(onClick = onClick, onLongClick = null)
            .padding(horizontal = 16.dp),
    )
}

/** 2dp progress bar — active part tinted by the caller. */
@Composable
fun LpLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(ShapeTokens.full)
            .background(colors.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(activeColor),
        )
    }
}

// ------------------------------------------------------------------ Skeleton

/** Shimmer placeholder, 1200ms linear, +/-4% luminance (board 01 motion / 02 cards). */
@Composable
fun LpSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = ShapeTokens.md,
) {
    val colors = MaterialTheme.colorScheme
    val (base, highlight) = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        Color(0xFFE6E9ED) to Color(0xFFEFF2F5)
    } else {
        Color(0xFF1E2124) to Color(0xFF282B2E)
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )
    val density = LocalDensity.current
    val band = with(density) { 320.dp.toPx() }
    val offset = band * (progress * 2f - 1f)
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(offset - band, 0f),
        end = Offset(offset + band, 0f),
    )
    Box(modifier = modifier.background(brush, shape))
}

@Composable
fun LpSkeletonRow(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        LpSkeletonBox(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LpSkeletonBox(
                shape = ShapeTokens.xs,
                modifier = Modifier.fillMaxWidth(0.92f).height(12.dp),
            )
            LpSkeletonBox(
                shape = ShapeTokens.xs,
                modifier = Modifier.fillMaxWidth(0.64f).height(12.dp),
            )
            LpSkeletonBox(
                shape = ShapeTokens.xs,
                modifier = Modifier.fillMaxWidth(0.38f).height(10.dp),
            )
        }
    }
}
