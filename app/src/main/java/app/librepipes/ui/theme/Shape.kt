package app.librepipes.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Design board 04 — Corner radius. Radius carries hierarchy: the more transient the
 * surface, the rounder it is.
 */
object ShapeTokens {
    /** Video frame, fullscreen player. */
    val none: CornerBasedShape = RoundedCornerShape(0.dp)

    /** Badges, chips-in-thumbnail. */
    val xs: CornerBasedShape = RoundedCornerShape(4.dp)

    /** Snackbar, tooltip, text field. */
    val sm: CornerBasedShape = RoundedCornerShape(8.dp)

    /** Thumbnails, cards, mini player. */
    val md: CornerBasedShape = RoundedCornerShape(12.dp)

    /** Settings group, banner. */
    val lg: CornerBasedShape = RoundedCornerShape(16.dp)

    /** Bottom sheet (top only), dialog. */
    val xl: CornerBasedShape = RoundedCornerShape(28.dp)

    /** Buttons, chips, search bar, avatar, nav pill. */
    val full: CornerBasedShape = RoundedCornerShape(percent = 50)
}

/** M3 shape scheme wired from the board radii (extraSmall 4 … extraLarge 28). */
val ShapeScheme = Shapes(
    extraSmall = ShapeTokens.xs,
    small = ShapeTokens.sm,
    medium = ShapeTokens.md,
    large = ShapeTokens.lg,
    extraLarge = ShapeTokens.xl,
)
