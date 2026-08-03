package app.librepipes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import app.librepipes.ui.theme.color.DarkColors
import app.librepipes.ui.theme.color.DarkErrorRoles
import app.librepipes.ui.theme.color.DarkExtended
import app.librepipes.ui.theme.color.LightColors
import app.librepipes.ui.theme.color.LightErrorRoles
import app.librepipes.ui.theme.color.LightExtended
import app.librepipes.ui.theme.color.LocalExtendedColors

@Composable
fun LibrePipeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        useDynamic -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Semantic roles stay fixed when dynamic color overrides the wallpaper-derived tints
    // (design board 02): error family, success and the player scrim never change.
    val fixedError = if (darkTheme) DarkErrorRoles else LightErrorRoles
    val colorScheme = base.copy(
        error = fixedError.error,
        onError = fixedError.onError,
        errorContainer = fixedError.errorContainer,
        onErrorContainer = fixedError.onErrorContainer,
    )
    val extended = if (darkTheme) DarkExtended else LightExtended

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ShapeScheme,
            content = content,
        )
    }
}
