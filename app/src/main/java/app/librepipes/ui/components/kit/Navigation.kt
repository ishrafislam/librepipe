package app.librepipes.ui.components.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.librepipes.ui.theme.ShapeTokens

/**
 * Design board 02-D — Navigation.
 */

/** 80dp bottom bar, surfaceContainer. Selected item = 64x32 primaryContainer pill + filled icon + 6dp primary unread dot. */
@Composable
fun LpBottomBar(
    items: List<LpNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(ShapeTokens.full)
                    .background(if (selected) colors.primaryContainer else Color.Transparent)
                    .clickable(onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) item.iconFilled else item.icon,
                    contentDescription = item.label,
                    tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                if (item.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 10.dp)
                            .size(6.dp)
                            .clip(ShapeTokens.full)
                            .background(colors.primary),
                    )
                }
            }
        }
    }
}

data class LpNavItem(
    val label: String,
    val icon: ImageVector,
    val iconFilled: ImageVector,
    val unreadCount: Int = 0,
)

/** Top app bar: 64dp, surface tint as scrim. */
@Composable
fun LpTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigationClick: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    actions: List<LpIconAction> = emptyList(),
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null && onNavigationClick != null) {
            Icon(
                imageVector = navigationIcon,
                contentDescription = "Back",
                tint = colors.onSurface,
                modifier = Modifier
                    .size(48.dp)
                    .padding(12.dp)
                    .clickable(onClick = onNavigationClick),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (navigationIcon != null) 4.dp else 16.dp, end = 16.dp),
        )
        actions.forEach { action ->
            Icon(
                imageVector = action.icon,
                contentDescription = action.contentDescription,
                tint = colors.onSurface,
                modifier = Modifier
                    .size(48.dp)
                    .padding(12.dp)
                    .clickable(onClick = action.onClick),
            )
        }
    }
}

data class LpIconAction(
    val icon: ImageVector,
    val contentDescription: String?,
    val onClick: () -> Unit,
)

/** 48dp icon button, surfaceContainer pill background. */
@Composable
fun LpRoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(ShapeTokens.full)
            .background(colors.surfaceContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(24.dp))
    }
}
