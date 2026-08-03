package app.librepipes.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design board 04 — Spacing scale. 4dp base grid; only these nine values exist.
 * If a gap needs 18dp, the layout is wrong.
 */
object Spacing {
    /** 4dp — icon↔label, badge padding. */
    val space1: Dp = 4.dp

    /** 8dp — title↔metadata. */
    val space2: Dp = 8.dp

    /** 12dp — thumbnail↔text (list rows). */
    val space3: Dp = 12.dp

    /** 16dp — screen side margin, row padding. */
    val space4: Dp = 16.dp

    /** 20dp — card↔card vertical. */
    val space5: Dp = 20.dp

    /** 24dp — section gap, sheet padding. */
    val space6: Dp = 24.dp

    /** 32dp — major section break. */
    val space8: Dp = 32.dp

    /** 40dp — empty-state icon↔title. */
    val space10: Dp = 40.dp

    /** 48dp — empty-state block inset. */
    val space12: Dp = 48.dp
}
