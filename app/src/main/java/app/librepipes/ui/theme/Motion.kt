package app.librepipes.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing

/**
 * Design board 05 — Motion. Short, flat, never in the way: nothing exceeds 500ms.
 * Honor Settings.Global.ANIMATOR_DURATION_SCALE; at 0 every transition is a cut.
 */
object Motion {

    /** 100ms — ripple, icon toggle. */
    const val Short2: Int = 100

    /** 200ms — chip select, switch. */
    const val Short4: Int = 200

    /** 300ms — nav destination, dialog. */
    const val Medium2: Int = 300

    /** 400ms — bottom sheet, mini→full player. */
    const val Medium4: Int = 400

    /** 500ms — splash → home hand-off. */
    const val Long2: Int = 500

    /** (.2,0,0,1) — anything that enters or leaves the screen. */
    val Emphasized: Easing = FastOutSlowInEasing

    /** (0,0,0,1) — sheets and menus opening. */
    val Decelerate: Easing = LinearOutSlowInEasing

    /** (.3,0,1,1) — sheets and menus dismissing. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** (.2,0,0,1) — on-screen property changes. */
    val Standard: Easing = FastOutSlowInEasing

    /** 1200ms linear shimmer, ±4% luminance — loading skeletons. */
    const val ShimmerMillis: Int = 1200
}
