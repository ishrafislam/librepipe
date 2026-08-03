package app.librepipes.ui.components.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.librepipes.ui.theme.ShapeTokens

/**
 * Design board 02-B — Inputs & selection.
 * Search bar 56dp pill; text field 56dp shape-sm; switch 52x32dp; every control
 * sits inside a 48dp target.
 */

@Composable
fun LpSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search Librepipe",
    onBack: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val active = focused || value.isNotEmpty()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(ShapeTokens.full)
            .background(colors.surfaceContainer)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) colors.primary else Color.Transparent,
                shape = ShapeTokens.full,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active && onBack != null) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp)
                        .clickable(onClick = onBack),
                )
            } else {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(
                    color = colors.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                ),
                cursorBrush = SolidColor(colors.primary),
                singleLine = true,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && !focused) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.outline,
                            )
                        }
                        inner()
                    }
                },
            )
            if (active) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Clear",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp)
                        .clickable { onValueChange("") },
                )
            } else {
                Icon(
                    Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
fun LpOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    placeholder: String = "",
    isError: Boolean = false,
    helperText: String? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val borderColor = when {
        !enabled -> colors.outlineVariant
        isError -> colors.error
        else -> colors.outline
    }
    val borderWidth = if (isError) 2.dp else 1.dp
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(ShapeTokens.sm)
                .background(if (enabled) Color.Transparent else colors.surfaceVariant)
                .border(borderWidth, borderColor, ShapeTokens.sm)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        !enabled -> colors.onSurface.copy(alpha = 0.38f)
                        isError -> colors.error
                        else -> colors.primary
                    },
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (enabled) colors.outline else colors.onSurface.copy(alpha = 0.38f),
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
        if (isError && helperText != null) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }
    }
}

@Composable
fun LpSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val trackColor by animateColorAsState(
        if (checked) colors.primary else colors.onSurface.copy(alpha = 0.12f),
        label = "track",
    )
    val thumbSize by animateDpAsState(if (checked) 24.dp else 16.dp, label = "thumb")
    val thumbOffset by animateDpAsState(if (checked) 20.dp else 0.dp, label = "offset")
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(if (checked) 0.dp else 2.dp, colors.outline, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (checked) colors.onPrimary else colors.outline),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun LpCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (checked) colors.primary else Color.Transparent)
            .border(if (checked) 0.dp else 2.dp, colors.onSurfaceVariant, RoundedCornerShape(2.dp))
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
fun LpRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val borderColor = if (selected) colors.primary else colors.onSurfaceVariant
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(2.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
            )
        }
    }
}

@Composable
fun LpFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dropdown: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(ShapeTokens.xs)
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .border(if (selected) 0.dp else 1.dp, colors.outline, ShapeTokens.xs)
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        )
        if (dropdown) {
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
