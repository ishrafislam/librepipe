package app.librepipes.ui.theme.color

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fixed semantic roles (design board 02 — Color): dynamic color may override primary,
 * secondary, tertiary and surface tints, but the roles below stay fixed in every theme.
 */
internal data class ErrorRoles(
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

internal val LightErrorRoles = ErrorRoles(
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/** Light scheme — design board 02: roles seeded from sky blue #4AA8E8. */
val LightColors = lightColorScheme(
    primary = Color(0xFF0A6DB5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E7F9),
    onPrimaryContainer = Color(0xFF00294A),
    secondary = Color(0xFF51606F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E3F1),
    onSecondaryContainer = Color(0xFF131C29),
    tertiary = Color(0xFF4F5B70),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E3F8),
    onTertiaryContainer = Color(0xFF101A2C),
    error = LightErrorRoles.error,
    onError = LightErrorRoles.onError,
    errorContainer = LightErrorRoles.errorContainer,
    onErrorContainer = LightErrorRoles.onErrorContainer,
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFE3E6EA),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceTint = Color(0xFF0A6DB5),
    inverseSurface = Color(0xFF303438),
    inverseOnSurface = Color(0xFFF2F3F5),
    inversePrimary = Color(0xFF9CCBFA),
    surfaceDim = Color(0xFFDADDE1),
    surfaceBright = Color(0xFFFBFCFE),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F6F9),
    surfaceContainer = Color(0xFFEFF1F4),
    surfaceContainerHigh = Color(0xFFE9EBEF),
    surfaceContainerHighest = Color(0xFFE3E6EA),
    outline = Color(0xFF72777F),
    outlineVariant = Color(0xFFC2C7CF),
    scrim = Color.Black,
)
