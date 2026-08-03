package app.librepipes.ui.theme.color

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Roles outside the M3 [androidx.compose.material3.ColorScheme]: success and the player
 * scrim. Both stay fixed regardless of dynamic color (design board 02).
 */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val playerScrim: Color = Color(0x99000000),
)

val LightExtended = ExtendedColors(
    success = Color(0xFF2E6B36),
    onSuccess = Color.White,
)

val DarkExtended = ExtendedColors(
    success = Color(0xFF8FD897),
    onSuccess = Color(0xFF00380B),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }
