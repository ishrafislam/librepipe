package app.librepipes.ui.components.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.librepipes.ui.theme.ShapeTokens

/**
 * Design board 02-A — Buttons. 40dp tall, shape-full, labelLarge, 24dp horizontal
 * padding (16dp with a leading icon, 12dp for text buttons).
 * State layers (design board 01): hover 8%, focus 10% + 3dp ring, pressed 10% + ripple,
 * disabled container 12% / content 38% — M3 defaults already match.
 */

@Composable
fun LpFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    LpButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled, leadingIcon = leadingIcon)
}

@Composable
fun LpSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ShapeTokens.full,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            disabledContainerColor = colors.onSurface.copy(alpha = 0.12f),
            disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
        ),
        contentPadding = if (leadingIcon != null) PaddingValues(start = 12.dp, end = 16.dp) else PaddingValues(horizontal = 24.dp),
    ) {
        LpButtonLabel(text, leadingIcon)
    }
}

@Composable
fun LpOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ShapeTokens.full,
        border = BorderStroke(1.dp, colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.primary,
            disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
        ),
        contentPadding = if (leadingIcon != null) PaddingValues(start = 12.dp, end = 16.dp) else PaddingValues(horizontal = 24.dp),
    ) {
        LpButtonLabel(text, leadingIcon)
    }
}

@Composable
fun LpTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ShapeTokens.full,
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (destructive) colors.error else colors.primary,
            disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LpIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun LpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    leadingIcon: ImageVector?,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ShapeTokens.full,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.onSurface.copy(alpha = 0.12f),
            disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
        ),
        contentPadding = if (leadingIcon != null) PaddingValues(start = 12.dp, end = 16.dp) else PaddingValues(horizontal = 24.dp),
    ) {
        LpButtonLabel(text, leadingIcon)
    }
}

@Composable
private fun LpButtonLabel(text: String, leadingIcon: ImageVector?) {
    if (leadingIcon != null) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}
