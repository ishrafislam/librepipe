package app.librepipes.ui.theme.color

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val DarkErrorRoles = ErrorRoles(
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Dark scheme — design board 02: true-dark surface base for OLED. */
val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCBFA),
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B71),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF24313D),
    secondaryContainer = Color(0xFF3C4854),
    onSecondaryContainer = Color(0xFFD4E4F6),
    tertiary = Color(0xFFC4CDE0),
    onTertiary = Color(0xFF1A2632),
    tertiaryContainer = Color(0xFF414C58),
    onTertiaryContainer = Color(0xFFE1E6F0),
    error = DarkErrorRoles.error,
    onError = DarkErrorRoles.onError,
    errorContainer = DarkErrorRoles.errorContainer,
    onErrorContainer = DarkErrorRoles.onErrorContainer,
    background = Color(0xFF111416),
    onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF333538),
    onSurfaceVariant = Color(0xFFC2C7CF),
    surfaceTint = Color(0xFF9CCBFA),
    inverseSurface = Color(0xFFE3E6EA),
    inverseOnSurface = Color(0xFF303438),
    inversePrimary = Color(0xFF004B71),
    surfaceDim = Color(0xFF111416),
    surfaceBright = Color(0xFF383B3D),
    surfaceContainerLowest = Color(0xFF0C0F11),
    surfaceContainerLow = Color(0xFF191C1E),
    surfaceContainer = Color(0xFF1D2022),
    surfaceContainerHigh = Color(0xFF282A2D),
    surfaceContainerHighest = Color(0xFF333538),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    scrim = Color.Black,
)
