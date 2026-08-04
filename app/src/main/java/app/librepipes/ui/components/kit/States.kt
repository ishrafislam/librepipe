package app.librepipes.ui.components.kit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.librepipes.ui.theme.PlexMono
import app.librepipes.ui.theme.ShapeTokens
import kotlinx.coroutines.delay

/**
 * Design board 05 — States. Empty: one 40dp outline icon in a 72dp circle,
 * headline, one neutral sentence, at most one action. Error: same frame plus a
 * technical mono code that is copyable, and a Retry action. Skeletons only
 * surface after 150ms so fast loads never flash.
 */

@Composable
fun LpStateIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
fun LpEmptyState(
    icon: ImageVector,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LpStateIcon(icon)
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            LpFilledButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun LpErrorState(
    message: String,
    modifier: Modifier = Modifier.fillMaxSize(),
    onRetry: (() -> Unit)? = null,
    code: String? = "UNKNOWN",
    icon: ImageVector = Icons.Rounded.ErrorOutline,
    title: String = "Something went wrong",
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LpStateIcon(icon)
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (code != null) {
            Spacer(Modifier.height(12.dp))
            CopyableCode(code)
        }
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            LpFilledButton(text = "Retry", onClick = onRetry)
        }
    }
}

@Composable
private fun CopyableCode(code: String) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    Row(
        modifier = Modifier
            .clip(ShapeTokens.xs)
            .background(colors.surfaceContainerHigh)
            .clickable {
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(ClipData.newPlainText("LibrePipe error code", code))
                copied = true
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = PlexMono,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (copied) "COPIED" else "COPY",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = PlexMono,
            color = colors.primary,
        )
    }
}

/**
 * Returns true only after [active] has stayed true for [delayMillis] — used to
 * gate skeletons so sub-150ms loads never flash one.
 */
@Composable
fun rememberDelayedSkeleton(active: Boolean, delayMillis: Long = 150): Boolean {
    val current = rememberUpdatedState(active)
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            delay(delayMillis)
            show = current.value
        } else {
            show = false
        }
    }
    return show
}

/** List-shaped loading skeleton: 160dp video rows, last row fades to 55%. */
@Composable
fun LpListSkeleton(rows: Int = 5, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(vertical = 8.dp)) {
        repeat(rows) { index ->
            val fade = index == rows - 1
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .then(
                        if (fade) Modifier.graphicsLayer { alpha = 0.55f } else Modifier,
                    ),
            ) {
                LpSkeletonRow()
            }
        }
    }
}

/** Feed-shaped loading skeleton: chips + two sections of 16:9 cards. */
@Composable
fun LpFeedSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LpSkeletonBox(shape = ShapeTokens.xs, modifier = Modifier.size(width = 76.dp, height = 32.dp))
            LpSkeletonBox(shape = ShapeTokens.xs, modifier = Modifier.size(width = 96.dp, height = 32.dp))
            LpSkeletonBox(shape = ShapeTokens.xs, modifier = Modifier.size(width = 64.dp, height = 32.dp))
        }
        LpSkeletonBox(
            shape = ShapeTokens.xs,
            modifier = Modifier.padding(start = 16.dp).size(width = 120.dp, height = 20.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(3) {
                LpSkeletonBox(
                    modifier = Modifier
                        .width(200.dp)
                        .aspectRatio(16f / 9f),
                )
            }
        }
        LpSkeletonBox(
            shape = ShapeTokens.xs,
            modifier = Modifier.padding(start = 16.dp).size(width = 120.dp, height = 20.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(3) {
                LpSkeletonBox(
                    modifier = Modifier
                        .width(200.dp)
                        .aspectRatio(16f / 9f),
                )
            }
        }
    }
}
