package app.librepipes.ui.components.kit

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.ui.theme.Motion
import app.librepipes.ui.theme.ShapeTokens
import app.librepipes.util.Format

/**
 * Design board 02-E — Overlays & feedback.
 */

/**
 * Modal sheet: 28dp top corners, 32x4 handle, 32% scrim, max 60% height,
 * 400ms decelerate slide. Content scrolls internally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LpSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Supply on a drill-down page to show a back arrow beside the title. */
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true },
    )
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(ShapeTokens.full)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(48.dp)
                            .clip(ShapeTokens.full)
                            .clickable(onClick = onBack)
                            .padding(12.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = if (onBack != null) 4.dp else 24.dp,
                            end = 24.dp,
                            top = 16.dp,
                            bottom = 16.dp,
                        ),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // A floor keeps a page swap from collapsing the content toward zero,
                    // which re-anchors the sheet and settles it to Hidden — i.e. the
                    // sheet closing instead of navigating.
                    .heightIn(min = 200.dp, max = (screenHeight * 0.6f).dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                content()
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * Dialog with destructive action support. The destructive action is an
 * error-colored text button — never a filled red button.
 */@Composable
fun LpDialog(
    title: String,
    text: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = ShapeTokens.lg,
        color = colors.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (content != null) {
                Box(modifier = Modifier.padding(top = 16.dp)) { content() }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                ) {
                    Text(
                        confirmLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (destructive) colors.error else colors.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Context menu: 8dp corners, 48dp rows, containerHigh, destructive item last after a divider. */
@Composable
fun LpContextMenu(
    title: String?,
    items: List<LpMenuItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(min = 180.dp, max = 320.dp),
        shape = ShapeTokens.sm,
        color = colors.surfaceContainerHigh,
    ) {
        Column {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            val destructiveIndex = items.indexOfFirst { it.destructive }
            items.forEachIndexed { index, item ->
                if (destructiveIndex in 0 until index) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(1.dp)
                            .background(colors.outlineVariant),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable(onClick = {
                            item.onClick()
                            onDismiss()
                        })
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.icon != null) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (item.destructive) colors.error else colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (item.destructive) colors.error else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

data class LpMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** 48dp inverseSurface snackbar, 4s (10s if action), action always "Undo". */
@Composable
fun LpSnackbar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onUndo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeTokens.xs)
            .background(colors.inverseSurface)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inverseOnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUndo) {
            Text(
                text = "Undo",
                style = MaterialTheme.typography.labelLarge,
                color = colors.inversePrimary,
            )
        }
    }
}
